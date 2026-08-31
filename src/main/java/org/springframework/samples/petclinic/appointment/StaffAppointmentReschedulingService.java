package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StaffAppointmentReschedulingService {

	private static final Logger log = LoggerFactory.getLogger(StaffAppointmentReschedulingService.class);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public StaffAppointmentReschedulingService(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, CalendarMutationCoordinator calendarCoordinator,
			CapacityConflictService capacityConflictService, AppointmentLifecyclePolicy lifecyclePolicy,
			ProtectedPayloadService payloadService, OwnerHistoryService ownerHistoryService, AuditService auditService,
			Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.lifecyclePolicy = lifecyclePolicy;
		this.payloadService = payloadService;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public record RescheduleAppointmentCommand(Long appointmentId, Long actorAccountId, Integer newVetId,
			Instant newStartAt, Instant newEndAt, boolean ownerAgreementRecorded, String agreementMedium,
			String internalReason, String ownerExplanation) {
	}

	public Appointment rescheduleAppointment(RescheduleAppointmentCommand cmd) {
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

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canReschedule(appointment, now)) {
				throw new IllegalStateException("Appointment cannot be rescheduled in state: "
						+ appointment.getBookingState() + " / " + appointment.getOutcomeState());
			}

			// Temporarily change start/end to avoid self-collision during check
			Instant oldStart = appointment.getStartAt();
			Instant oldEnd = appointment.getEndAt();
			Integer oldVet = appointment.getVetId();

			// Validate conflicts
			if (this.capacityConflictService.hasOverlappingBlocker(cmd.newVetId(), appointment.getPetId(),
					appointment.getOwnerId(), cmd.newStartAt(), cmd.newEndAt())) {
				throw new AvailabilityConflictException("Selected appointment slot has a capacity conflict");
			}

			appointment.setVetId(cmd.newVetId());
			appointment.setStartAt(cmd.newStartAt());
			appointment.setEndAt(cmd.newEndAt());
			appointment.setUpdatedAt(now);
			Appointment saved = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "RESCHEDULE_REASON", 1,
					"text/plain", cmd.internalReason().trim());

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(saved.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "APPOINTMENT_RESCHEDULED", true, cmd.agreementMedium().trim());
			changeEvent.setOccurredAt(now);
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "APPOINTMENT_RESCHEDULED", "Appointment",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

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

}
