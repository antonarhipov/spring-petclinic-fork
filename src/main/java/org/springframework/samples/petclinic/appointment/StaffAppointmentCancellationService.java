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
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class StaffAppointmentCancellationService {

	private static final Logger log = LoggerFactory.getLogger(StaffAppointmentCancellationService.class);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public StaffAppointmentCancellationService(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, CalendarMutationCoordinator calendarCoordinator,
			AppointmentLifecyclePolicy lifecyclePolicy, ProtectedPayloadService payloadService,
			OwnerHistoryService ownerHistoryService, AuditService auditService, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.lifecyclePolicy = lifecyclePolicy;
		this.payloadService = payloadService;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public record StaffCancellationCommand(Long appointmentId, Long actorAccountId, String reasonCategory,
			String reasonDetails, String ownerExplanation, boolean ownerContacted) {
	}

	public Appointment cancelAppointmentByStaff(StaffCancellationCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.appointmentId(), "appointmentId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		if (cmd.reasonCategory() == null || cmd.reasonCategory().isBlank()) {
			throw new IllegalArgumentException("Cancellation reason category is required");
		}

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canStaffCancel(appointment, now)) {
				throw new IllegalStateException("Appointment cannot be cancelled in state: "
						+ appointment.getBookingState() + " / " + appointment.getOutcomeState());
			}

			appointment.setBookingState(BookingState.CANCELLED);
			appointment.setUpdatedAt(now);
			Appointment saved = this.appointmentRepository.save(appointment);

			String fullReason = cmd.reasonCategory().trim()
					+ (cmd.reasonDetails() != null && !cmd.reasonDetails().isBlank() ? ": " + cmd.reasonDetails().trim()
							: "");
			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "STAFF_CANCELLATION_REASON",
					1, "text/plain", fullReason);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(saved.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "STAFF_CANCELLED", cmd.ownerContacted(), cmd.ownerContacted() ? "PHONE" : null);
			changeEvent.setOccurredAt(now);
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "STAFF_CANCELLED_APPOINTMENT", "Appointment",
					saved.getId().toString(), "SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

			this.ownerHistoryService.recordOwnerHistory(saved.getOwnerId(), saved.getPetId(),
					saved.getOriginatingRequestId(), "APPOINTMENT_CANCELLED",
					"Appointment for " + saved.getStartAt() + " was cancelled by clinic"
							+ (cmd.ownerExplanation() != null && !cmd.ownerExplanation().isBlank()
									? ": " + cmd.ownerExplanation().trim() : ""),
					null);

			log.info("Staff account {} cancelled appointment {}", cmd.actorAccountId(), saved.getId());
			return saved;
		});
	}

}
