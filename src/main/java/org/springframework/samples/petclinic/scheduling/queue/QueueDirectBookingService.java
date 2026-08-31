package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEvent;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEventRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QueueDirectBookingService {

	private static final Logger log = LoggerFactory.getLogger(QueueDirectBookingService.class);

	private final QueueItemRepository queueItemRepository;

	private final SchedulingRequestRepository requestRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public QueueDirectBookingService(QueueItemRepository queueItemRepository,
			SchedulingRequestRepository requestRepository, ActiveSchedulingRequestRepository activeRequestRepository,
			AppointmentRepository appointmentRepository, AppointmentChangeEventRepository changeEventRepository,
			CalendarMutationCoordinator calendarCoordinator, CapacityConflictService capacityConflictService,
			EffectiveAvailabilityService effectiveAvailabilityService, OwnerRepository ownerRepository,
			VetRepository vetRepository, ProtectedPayloadService payloadService, AuditService auditService,
			OwnerHistoryService ownerHistoryService, Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.requestRepository = requestRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.payloadService = payloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public record QueueDirectBookCommand(Long queueItemId, Long actorAccountId, Integer vetId, Instant startAt,
			Instant endAt, boolean ownerAgreementRecorded, String agreementMedium, String internalReason) {
	}

	public Appointment directBookFromQueue(QueueDirectBookCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.queueItemId(), "queueItemId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.vetId(), "vetId must not be null");
		Objects.requireNonNull(cmd.startAt(), "startAt must not be null");
		Objects.requireNonNull(cmd.endAt(), "endAt must not be null");

		if (!cmd.ownerAgreementRecorded()) {
			throw new IllegalArgumentException("Owner agreement must be recorded to book directly from queue");
		}
		if (cmd.internalReason() == null || cmd.internalReason().isBlank()) {
			throw new IllegalArgumentException("Internal reason is required to book directly from queue");
		}

		QueueItem queueItem = this.queueItemRepository.findById(cmd.queueItemId())
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + cmd.queueItemId()));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot direct-book inactive queue item in state: " + queueItem.getState());
		}

		SchedulingRequest request = queueItem.getRequest();
		Owner owner = this.ownerRepository.findById(request.getOwnerId())
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + request.getOwnerId()));
		Pet pet = owner.getPet(request.getPetId());
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found: " + request.getPetId());
		}
		Vet vet = this.vetRepository.findById(cmd.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + cmd.vetId()));

		return this.calendarCoordinator.executeWithLock(() -> {
			BookingConflictCheck check = this.capacityConflictService.checkDirectBookingConflicts(cmd.vetId(),
					request.getOwnerId(), request.getPetId(), cmd.startAt(), cmd.endAt());
			if (!check.valid()) {
				throw new AvailabilityConflictException(
						"Booking conflicts: " + String.join(", ", check.errorMessages()));
			}

			Instant now = this.clock.instant();
			String zoneIdStr = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();

			Appointment appointment = new Appointment(request.getOwnerId(), request.getPetId(), cmd.vetId(),
					cmd.startAt(), cmd.endAt(), zoneIdStr);
			appointment.setOriginatingRequestId(request.getId());
			Appointment savedAppointment = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "DIRECT_BOOKING_REASON", 1,
					"text/plain", cmd.internalReason().trim());

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppointment.getId(),
					cmd.actorAccountId(), "STAFF", "DIRECT_BOOKED", true, cmd.agreementMedium());
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			this.changeEventRepository.save(changeEvent);

			request.setState(RequestState.CONFIRMED);
			request.setAppointmentId(savedAppointment.getId());
			request.setClosedAt(now);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);
			this.activeRequestRepository.deleteByRequestId(request.getId());

			queueItem.setState(QueueState.RESOLVED);
			queueItem.setResolvedAt(now);
			queueItem.setAwaitingReason(null);
			queueItem.setUpdatedAt(now);
			this.queueItemRepository.save(queueItem);

			this.auditService.recordEvent(cmd.actorAccountId(), "DIRECT_BOOK_FROM_QUEUE", "QueueItem",
					queueItem.getId().toString(), "SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

			ZoneId zoneId = ZoneId.of(zoneIdStr);
			String formattedTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
				.format(ZonedDateTime.ofInstant(cmd.startAt(), zoneId));
			this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
					"APPOINTMENT_CONFIRMED", "Appointment scheduled directly for " + pet.getName() + " with Dr. "
							+ vet.getLastName() + " at " + formattedTime,
					null);

			log.info("Directly booked appointment {} from queue item {} (request {})", savedAppointment.getId(),
					queueItem.getId(), request.getId());
			return savedAppointment;
		});
	}

}
