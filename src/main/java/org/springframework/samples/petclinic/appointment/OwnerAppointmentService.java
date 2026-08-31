package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Map;
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
public class OwnerAppointmentService {

	private static final Logger log = LoggerFactory.getLogger(OwnerAppointmentService.class);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public OwnerAppointmentService(AppointmentRepository appointmentRepository,
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

	public Appointment cancelAppointmentByOwner(Long appointmentId, Integer ownerId, String reason) {
		Objects.requireNonNull(appointmentId, "appointmentId must not be null");
		Objects.requireNonNull(ownerId, "ownerId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findByIdAndOwnerId(appointmentId, ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found for owner"));
			BookingState priorState = appointment.getBookingState();

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canOwnerCancel(appointment, now)) {
				throw new IllegalStateException("Appointment cannot be cancelled by owner (status: "
						+ appointment.getBookingState() + ", start: " + appointment.getStartAt() + ")");
			}

			appointment.setBookingState(BookingState.CANCELLED);
			appointment.setUpdatedAt(now);
			Appointment saved = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = null;
			if (reason != null && !reason.isBlank()) {
				reasonPayload = this.payloadService.store(UUID.randomUUID(), "CANCELLATION_REASON", 1, "text/plain",
						reason.trim());
			}

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(saved.getId(), null, "ROLE_OWNER",
					"OWNER_CANCELLED", true, "ONLINE");
			changeEvent.setOccurredAt(now);
			if (reasonPayload != null) {
				changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			}
			this.changeEventRepository.save(changeEvent);

			this.ownerHistoryService.recordOwnerHistory(ownerId, saved.getPetId(), saved.getOriginatingRequestId(),
					"APPOINTMENT_CANCELLED", "Appointment for " + saved.getStartAt() + " was cancelled"
							+ (reason != null && !reason.isBlank() ? ": " + reason.trim() : ""),
					null);
			this.auditService.recordStructuredEvent(null, "OWNER_CANCELLED_APPOINTMENT", "Appointment",
					saved.getId().toString(), "SUCCESS", null, null,
					Map.of("bookingState", priorState.name(), "startAt", saved.getStartAt()),
					Map.of("bookingState", saved.getBookingState().name(), "cancelledAt", now));

			log.info("Owner {} cancelled appointment {}", ownerId, appointmentId);
			return saved;
		});
	}

}
