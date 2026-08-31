package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Set;
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

	private static final Set<String> REASON_CATEGORIES = Set.of("OWNER_REQUEST", "CLINICAL_NEED", "FOLLOW_UP", "OTHER");

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
			Instant endAt, boolean ownerAgreementRecorded, String agreementMedium, String reasonCategory,
			String internalReason, Long expectedRequestVersion, Integer expectedWorkflowRevision,
			Long expectedQueueVersion) {
		public QueueDirectBookCommand(Long queueItemId, Long actorAccountId, Integer vetId, Instant startAt,
				Instant endAt, boolean ownerAgreementRecorded, String agreementMedium, String internalReason,
				Long expectedRequestVersion, Integer expectedWorkflowRevision, Long expectedQueueVersion) {
			this(queueItemId, actorAccountId, vetId, startAt, endAt, ownerAgreementRecorded, agreementMedium, "OTHER",
					internalReason, expectedRequestVersion, expectedWorkflowRevision, expectedQueueVersion);
		}
	}

	public record QueueDirectBookReview(Long queueItemId, String ownerName, String petName, String veterinarianName,
			Instant startAt, Instant endAt, String zoneId, String agreementMedium, String reasonCategory,
			String internalReason) {
	}

	public QueueDirectBookReview reviewDirectBooking(QueueDirectBookCommand cmd) {
		ValidatedBooking booking = validateAndLoad(cmd);
		ensureSlotIsEligible(cmd, booking);
		return new QueueDirectBookReview(cmd.queueItemId(),
				booking.owner().getFirstName() + " " + booking.owner().getLastName(), booking.pet().getName(),
				booking.vet().getFirstName() + " " + booking.vet().getLastName(), cmd.startAt(), cmd.endAt(),
				booking.zoneId(), cmd.agreementMedium(), cmd.reasonCategory(), cmd.internalReason().trim());
	}

	public Appointment directBookFromQueue(QueueDirectBookCommand cmd) {
		ValidatedBooking booking = validateAndLoad(cmd);

		return this.calendarCoordinator.executeWithLock(() -> {
			ensureSlotIsEligible(cmd, booking);

			QueueItem queueItem = booking.queueItem();
			SchedulingRequest request = booking.request();
			Pet pet = booking.pet();
			Vet vet = booking.vet();
			Instant now = this.clock.instant();
			String zoneIdStr = booking.zoneId();

			Appointment appointment = new Appointment(request.getOwnerId(), request.getPetId(), cmd.vetId(),
					cmd.startAt(), cmd.endAt(), zoneIdStr);
			appointment.setOriginatingRequestId(request.getId());
			Appointment savedAppointment = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "DIRECT_BOOKING_REASON", 1,
					"text/plain", cmd.reasonCategory() + ": " + cmd.internalReason().trim());
			String snapshot = "{\"before\":null,\"after\":{\"ownerId\":" + request.getOwnerId() + ",\"petId\":"
					+ request.getPetId() + ",\"vetId\":" + cmd.vetId() + ",\"startAt\":\"" + cmd.startAt()
					+ "\",\"endAt\":\"" + cmd.endAt() + "\",\"zoneId\":\"" + zoneIdStr
					+ "\",\"bookingState\":\"CONFIRMED\"}}";
			ProtectedPayload snapshotPayload = this.payloadService.store(UUID.randomUUID(),
					"QUEUE_DIRECT_BOOKING_SNAPSHOT", 1, "application/json", snapshot);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppointment.getId(),
					cmd.actorAccountId(), "STAFF", "DIRECT_BOOKED", true, cmd.agreementMedium());
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			changeEvent.setProtectedSnapshotPayloadId(snapshotPayload.getId());
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
					queueItem.getId().toString(), "SUCCESS", UUID.randomUUID(), null, snapshotPayload.getId());

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

	private ValidatedBooking validateAndLoad(QueueDirectBookCommand cmd) {
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
		if (!REASON_CATEGORIES.contains(cmd.reasonCategory())) {
			throw new IllegalArgumentException("Select a valid booking reason category");
		}

		QueueItem queueItem = this.queueItemRepository.findById(cmd.queueItemId())
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + cmd.queueItemId()));
		queueItem.requireExpectedVersions(cmd.expectedRequestVersion(), cmd.expectedWorkflowRevision(),
				cmd.expectedQueueVersion());

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot direct-book inactive queue item in state: " + queueItem.getState());
		}
		queueItem.requireAssignedTo(cmd.actorAccountId());

		SchedulingRequest request = queueItem.getRequest();
		Owner owner = this.ownerRepository.findById(request.getOwnerId())
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + request.getOwnerId()));
		Pet pet = owner.getPet(request.getPetId());
		if (pet == null) {
			throw new IllegalArgumentException("Pet not found: " + request.getPetId());
		}
		Vet vet = this.vetRepository.findById(cmd.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + cmd.vetId()));
		if (request.getCurrentWorkflowRevision() != null
				&& request.getCurrentWorkflowRevision().getRequiredSpecialtyId() != null) {
			Integer specialtyId = request.getCurrentWorkflowRevision().getRequiredSpecialtyId();
			if (vet.getSpecialties().stream().noneMatch(specialty -> specialtyId.equals(specialty.getId()))) {
				throw new AvailabilityConflictException("Selected veterinarian does not have the required specialty");
			}
		}

		String zoneId = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
		return new ValidatedBooking(queueItem, request, owner, pet, vet, zoneId);
	}

	private void ensureSlotIsEligible(QueueDirectBookCommand cmd, ValidatedBooking booking) {
		BookingConflictCheck check = this.capacityConflictService.checkStaffBookingConflicts(cmd.vetId(),
				booking.request().getOwnerId(), booking.request().getPetId(), cmd.startAt(), cmd.endAt());
		if (!check.valid()) {
			throw new AvailabilityConflictException("Booking conflicts: " + String.join(", ", check.errorMessages()));
		}
	}

	private record ValidatedBooking(QueueItem queueItem, SchedulingRequest request, Owner owner, Pet pet, Vet vet,
			String zoneId) {
	}

}
