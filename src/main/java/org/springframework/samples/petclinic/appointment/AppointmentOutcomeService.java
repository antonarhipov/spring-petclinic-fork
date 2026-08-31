package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitProvenance;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppointmentOutcomeService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentOutcomeService.class);

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final AppointmentOutcomeEventRepository outcomeEventRepository;

	private final VisitRepository visitRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public AppointmentOutcomeService(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository,
			AppointmentOutcomeEventRepository outcomeEventRepository, VisitRepository visitRepository,
			CalendarMutationCoordinator calendarCoordinator, EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentLifecyclePolicy lifecyclePolicy, ProtectedPayloadService payloadService,
			OwnerHistoryService ownerHistoryService, AuditService auditService, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.outcomeEventRepository = outcomeEventRepository;
		this.visitRepository = visitRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.lifecyclePolicy = lifecyclePolicy;
		this.payloadService = payloadService;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public record CompleteAppointmentCommand(Long appointmentId, Long actorAccountId, String clinicalNotes,
			String ownerVisibleSummary) {
	}

	public record NoShowCommand(Long appointmentId, Long actorAccountId, String reason) {
	}

	public Visit completeAppointment(CompleteAppointmentCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.appointmentId(), "appointmentId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canComplete(appointment, now)) {
				throw new IllegalStateException("Appointment cannot be completed in current state: "
						+ appointment.getBookingState() + " / " + appointment.getOutcomeState());
			}

			appointment.setOutcomeState(OutcomeState.COMPLETED);
			appointment.setUpdatedAt(now);
			Appointment savedAppt = this.appointmentRepository.save(appointment);

			ProtectedPayload clinicalPayload = null;
			if (cmd.clinicalNotes() != null && !cmd.clinicalNotes().isBlank()) {
				clinicalPayload = this.payloadService.store(UUID.randomUUID(), "CLINICAL_NOTES", 1, "text/plain",
						cmd.clinicalNotes().trim());
			}

			String zoneIdStr = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
			LocalDate visitDate = LocalDate.ofInstant(savedAppt.getStartAt(), ZoneId.of(zoneIdStr));
			String summary = (cmd.ownerVisibleSummary() != null && !cmd.ownerVisibleSummary().isBlank())
					? cmd.ownerVisibleSummary().trim() : "Routine care completed";

			Visit visit = new Visit();
			visit.setPetId(savedAppt.getPetId());
			visit.setDate(visitDate);
			visit.setDescription(summary);
			visit.setProvenance(VisitProvenance.APPOINTMENT_COMPLETION);
			visit.setAppointmentId(savedAppt.getId());
			visit.setVetId(savedAppt.getVetId());
			visit.setProtectedClinicalPayloadId(clinicalPayload != null ? clinicalPayload.getId() : null);

			Visit savedVisit = this.visitRepository.save(visit);

			AppointmentOutcomeEvent outcomeEvent = new AppointmentOutcomeEvent(savedAppt.getId(),
					AppointmentOutcomeEventType.COMPLETED, cmd.actorAccountId(), now, OutcomeState.PENDING,
					OutcomeState.COMPLETED, savedVisit.getId(), null,
					clinicalPayload != null ? clinicalPayload.getId() : null);
			AppointmentOutcomeEvent savedOutcomeEvent = this.outcomeEventRepository.save(outcomeEvent);

			savedVisit.setOutcomeEventId(savedOutcomeEvent.getId());
			this.visitRepository.save(savedVisit);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppt.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "APPOINTMENT_COMPLETED", true, "IN_PERSON");
			changeEvent.setOccurredAt(now);
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "APPOINTMENT_COMPLETED", "Appointment",
					savedAppt.getId().toString(), "SUCCESS", UUID.randomUUID(), null,
					clinicalPayload != null ? clinicalPayload.getId() : null);

			this.ownerHistoryService.recordOwnerHistory(savedAppt.getOwnerId(), savedAppt.getPetId(),
					savedAppt.getOriginatingRequestId(), "APPOINTMENT_COMPLETED",
					"Appointment completed: " + savedVisit.getDescription(), null);

			log.info("Completed appointment {} -> Created visit {}", savedAppt.getId(), savedVisit.getId());
			return savedVisit;
		});
	}

	public AppointmentOutcomeEvent recordNoShow(NoShowCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.appointmentId(), "appointmentId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canRecordNoShow(appointment, now)) {
				throw new IllegalStateException("Cannot record no-show for appointment in state: "
						+ appointment.getBookingState() + " / " + appointment.getOutcomeState());
			}

			appointment.setOutcomeState(OutcomeState.NO_SHOW);
			appointment.setUpdatedAt(now);
			Appointment savedAppt = this.appointmentRepository.save(appointment);

			ProtectedPayload reasonPayload = null;
			if (cmd.reason() != null && !cmd.reason().isBlank()) {
				reasonPayload = this.payloadService.store(UUID.randomUUID(), "NO_SHOW_REASON", 1, "text/plain",
						cmd.reason().trim());
			}

			AppointmentOutcomeEvent outcomeEvent = new AppointmentOutcomeEvent(savedAppt.getId(),
					AppointmentOutcomeEventType.NO_SHOW, cmd.actorAccountId(), now, OutcomeState.PENDING,
					OutcomeState.NO_SHOW, null, reasonPayload != null ? reasonPayload.getId() : null, null);
			AppointmentOutcomeEvent savedOutcomeEvent = this.outcomeEventRepository.save(outcomeEvent);

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppt.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "APPOINTMENT_NO_SHOW", false, null);
			changeEvent.setOccurredAt(now);
			if (reasonPayload != null) {
				changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			}
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "APPOINTMENT_NO_SHOW", "Appointment",
					savedAppt.getId().toString(), "SUCCESS", UUID.randomUUID(), null,
					reasonPayload != null ? reasonPayload.getId() : null);

			this.ownerHistoryService.recordOwnerHistory(savedAppt.getOwnerId(), savedAppt.getPetId(),
					savedAppt.getOriginatingRequestId(), "APPOINTMENT_NO_SHOW", "Appointment marked as no-show", null);

			log.info("Recorded no-show for appointment {}", savedAppt.getId());
			return savedOutcomeEvent;
		});
	}

}
