package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StaffAppointmentReschedulingService {

	private static final Set<String> REASON_CATEGORIES = Set.of("OWNER_REQUEST", "VET_UNAVAILABLE",
			"CLINIC_SCHEDULE_CHANGE", "CLINICAL_NEED", "OTHER");

	private static final Logger log = LoggerFactory.getLogger(StaffAppointmentReschedulingService.class);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final SchedulingRequestRepository requestRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public StaffAppointmentReschedulingService(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, CalendarMutationCoordinator calendarCoordinator,
			CapacityConflictService capacityConflictService, AppointmentLifecyclePolicy lifecyclePolicy,
			ProtectedPayloadService payloadService, OwnerHistoryService ownerHistoryService, AuditService auditService,
			SchedulingRequestRepository requestRepository, VetRepository vetRepository, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.lifecyclePolicy = lifecyclePolicy;
		this.payloadService = payloadService;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.requestRepository = requestRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	public record RescheduleAppointmentCommand(Long appointmentId, Long actorAccountId, Integer newVetId,
			Instant newStartAt, Instant newEndAt, boolean ownerAgreementRecorded, String agreementMedium,
			String reasonCategory, String internalReason, String ownerExplanation) {
		public RescheduleAppointmentCommand(Long appointmentId, Long actorAccountId, Integer newVetId,
				Instant newStartAt, Instant newEndAt, boolean ownerAgreementRecorded, String agreementMedium,
				String internalReason, String ownerExplanation) {
			this(appointmentId, actorAccountId, newVetId, newStartAt, newEndAt, ownerAgreementRecorded, agreementMedium,
					"OTHER", internalReason, ownerExplanation);
		}
	}

	public Appointment rescheduleAppointment(RescheduleAppointmentCommand cmd) {
		validateCommand(cmd);

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canReschedule(appointment, now)) {
				throw new IllegalStateException("Appointment cannot be rescheduled in state: "
						+ appointment.getBookingState() + " / " + appointment.getOutcomeState());
			}
			validateRequiredSpecialty(appointment, cmd.newVetId());

			Instant oldStart = appointment.getStartAt();
			Instant oldEnd = appointment.getEndAt();
			Integer oldVet = appointment.getVetId();

			CapacityConflictService.BookingConflictCheck check = this.capacityConflictService
				.checkStaffBookingConflictsExcludingAppointment(appointment.getId(), cmd.newVetId(),
						appointment.getOwnerId(), appointment.getPetId(), cmd.newStartAt(), cmd.newEndAt());
			if (!check.valid()) {
				throw new AvailabilityConflictException(
						"Selected appointment slot is unavailable: " + String.join(", ", check.errorMessages()));
			}

			appointment.setVetId(cmd.newVetId());
			appointment.setStartAt(cmd.newStartAt());
			appointment.setEndAt(cmd.newEndAt());
			appointment.setUpdatedAt(now);
			Appointment saved = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "RESCHEDULE_REASON", 1,
					"text/plain", cmd.reasonCategory() + ": " + cmd.internalReason().trim());
			String snapshot = "{\"before\":{\"vetId\":" + oldVet + ",\"startAt\":\"" + oldStart + "\",\"endAt\":\""
					+ oldEnd + "\"},\"after\":{\"vetId\":" + cmd.newVetId() + ",\"startAt\":\"" + cmd.newStartAt()
					+ "\",\"endAt\":\"" + cmd.newEndAt() + "\"}}";
			ProtectedPayload snapshotPayload = this.payloadService.store(UUID.randomUUID(), "RESCHEDULE_SNAPSHOT", 1,
					"application/json", snapshot);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(saved.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "APPOINTMENT_RESCHEDULED", true, cmd.agreementMedium().trim());
			changeEvent.setOccurredAt(now);
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			changeEvent.setProtectedSnapshotPayloadId(snapshotPayload.getId());
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "APPOINTMENT_RESCHEDULED", "Appointment",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, snapshotPayload.getId());

			this.ownerHistoryService.recordOwnerHistory(saved.getOwnerId(), saved.getPetId(),
					saved.getOriginatingRequestId(), "APPOINTMENT_RESCHEDULED",
					"Appointment rescheduled to " + cmd.newStartAt()
							+ (cmd.ownerExplanation() != null && !cmd.ownerExplanation().isBlank()
									? ": " + cmd.ownerExplanation().trim() : ""),
					null);

			log.info("Rescheduled appointment {} from {} to {}", saved.getId(), oldStart, cmd.newStartAt());
			return saved;
		});
	}

	@Transactional(readOnly = true)
	public BookingConflictCheck validateReschedule(RescheduleAppointmentCommand cmd) {
		validateCommand(cmd);
		Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));
		if (!this.lifecyclePolicy.canReschedule(appointment, this.clock.instant())) {
			return BookingConflictCheck.failed("Appointment cannot be rescheduled in its current state");
		}
		try {
			validateRequiredSpecialty(appointment, cmd.newVetId());
		}
		catch (AvailabilityConflictException ex) {
			return BookingConflictCheck.failed(ex.getMessage());
		}
		return this.capacityConflictService.checkStaffBookingConflictsExcludingAppointment(appointment.getId(),
				cmd.newVetId(), appointment.getOwnerId(), appointment.getPetId(), cmd.newStartAt(), cmd.newEndAt());
	}

	private void validateCommand(RescheduleAppointmentCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.appointmentId(), "appointmentId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.newVetId(), "newVetId must not be null");
		Objects.requireNonNull(cmd.newStartAt(), "newStartAt must not be null");
		Objects.requireNonNull(cmd.newEndAt(), "newEndAt must not be null");
		if (!cmd.ownerAgreementRecorded()) {
			throw new IllegalArgumentException("Owner agreement must be recorded before rescheduling");
		}
		if (cmd.agreementMedium() == null || cmd.agreementMedium().isBlank()) {
			throw new IllegalArgumentException("Agreement medium is required");
		}
		if (cmd.internalReason() == null || cmd.internalReason().isBlank()) {
			throw new IllegalArgumentException("Internal reason is required");
		}
		if (!REASON_CATEGORIES.contains(cmd.reasonCategory())) {
			throw new IllegalArgumentException("Select a valid rescheduling reason category");
		}
	}

	private void validateRequiredSpecialty(Appointment appointment, Integer vetId) {
		if (appointment.getOriginatingRequestId() == null) {
			return;
		}
		SchedulingRequest request = this.requestRepository.findById(appointment.getOriginatingRequestId()).orElse(null);
		if (request == null || request.getCurrentWorkflowRevision() == null
				|| request.getCurrentWorkflowRevision().getRequiredSpecialtyId() == null) {
			return;
		}
		Integer requiredSpecialtyId = request.getCurrentWorkflowRevision().getRequiredSpecialtyId();
		Vet vet = this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + vetId));
		if (vet.getSpecialties().stream().noneMatch(specialty -> requiredSpecialtyId.equals(specialty.getId()))) {
			throw new AvailabilityConflictException("Selected veterinarian does not have the required specialty");
		}
	}

}
