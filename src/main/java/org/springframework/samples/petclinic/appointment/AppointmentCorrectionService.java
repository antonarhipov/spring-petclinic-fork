package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
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
public class AppointmentCorrectionService {

	private static final Logger log = LoggerFactory.getLogger(AppointmentCorrectionService.class);

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

	public AppointmentCorrectionService(AppointmentRepository appointmentRepository,
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

	public record CorrectOutcomeCommand(Long appointmentId, Long actorAccountId, OutcomeState newOutcome, String reason,
			String clinicalNotes, String ownerVisibleSummary) {
	}

	public AppointmentOutcomeEvent correctOutcome(CorrectOutcomeCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.appointmentId(), "appointmentId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.newOutcome(), "newOutcome must not be null");
		if (cmd.reason() == null || cmd.reason().isBlank()) {
			throw new IllegalArgumentException("Correction reason must not be blank");
		}
		if (cmd.newOutcome() != OutcomeState.COMPLETED && cmd.newOutcome() != OutcomeState.NO_SHOW) {
			throw new IllegalArgumentException("New outcome must be COMPLETED or NO_SHOW");
		}

		return this.calendarCoordinator.executeWithLock(() -> {
			Appointment appointment = this.appointmentRepository.findById(cmd.appointmentId())
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + cmd.appointmentId()));

			Instant now = this.clock.instant();
			if (!this.lifecyclePolicy.canCorrectOutcome(appointment, now)) {
				throw new IllegalStateException(
						"Cannot correct outcome for appointment in state: " + appointment.getOutcomeState());
			}

			OutcomeState previousOutcome = appointment.getOutcomeState();
			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "CORRECTION_REASON", 1,
					"text/plain", cmd.reason().trim());

			ProtectedPayload clinicalPayload = null;
			Integer resultingVisitId = null;

			if (cmd.newOutcome() == OutcomeState.COMPLETED) {
				if (cmd.clinicalNotes() != null && !cmd.clinicalNotes().isBlank()) {
					clinicalPayload = this.payloadService.store(UUID.randomUUID(), "CLINICAL_NOTES", 1, "text/plain",
							cmd.clinicalNotes().trim());
				}

				String summary = (cmd.ownerVisibleSummary() != null && !cmd.ownerVisibleSummary().isBlank())
						? cmd.ownerVisibleSummary().trim() : "Routine care completed";

				Optional<Visit> existingVisitOpt = this.visitRepository.findByAppointmentId(appointment.getId());
				if (existingVisitOpt.isPresent()) {
					Visit existingVisit = existingVisitOpt.get();
					existingVisit.setDescription(summary);
					if (clinicalPayload != null) {
						existingVisit.setProtectedClinicalPayloadId(clinicalPayload.getId());
					}
					Visit saved = this.visitRepository.save(existingVisit);
					resultingVisitId = saved.getId();
				}
				else {
					String zoneIdStr = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
					LocalDate visitDate = LocalDate.ofInstant(appointment.getStartAt(), ZoneId.of(zoneIdStr));

					Visit newVisit = new Visit();
					newVisit.setPetId(appointment.getPetId());
					newVisit.setDate(visitDate);
					newVisit.setDescription(summary);
					newVisit.setProvenance(VisitProvenance.APPOINTMENT_COMPLETION);
					newVisit.setAppointmentId(appointment.getId());
					newVisit.setVetId(appointment.getVetId());
					newVisit.setProtectedClinicalPayloadId(clinicalPayload != null ? clinicalPayload.getId() : null);
					Visit saved = this.visitRepository.save(newVisit);
					resultingVisitId = saved.getId();
				}
			}
			else if (cmd.newOutcome() == OutcomeState.NO_SHOW) {
				this.visitRepository.findByAppointmentId(appointment.getId()).ifPresent(v -> {
					v.setAppointmentId(null);
					this.visitRepository.save(v);
				});
				resultingVisitId = null;
			}

			appointment.setOutcomeState(cmd.newOutcome());
			appointment.setUpdatedAt(now);
			Appointment savedAppt = this.appointmentRepository.save(appointment);

			AppointmentOutcomeEvent outcomeEvent = new AppointmentOutcomeEvent(savedAppt.getId(),
					AppointmentOutcomeEventType.CORRECTION, cmd.actorAccountId(), now, previousOutcome,
					cmd.newOutcome(), resultingVisitId, reasonPayload.getId(),
					clinicalPayload != null ? clinicalPayload.getId() : null);
			AppointmentOutcomeEvent savedOutcomeEvent = this.outcomeEventRepository.save(outcomeEvent);

			if (resultingVisitId != null) {
				this.visitRepository.findById(resultingVisitId).ifPresent(v -> {
					v.setOutcomeEventId(savedOutcomeEvent.getId());
					this.visitRepository.save(v);
				});
			}

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppt.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "APPOINTMENT_OUTCOME_CORRECTED", false, null);
			changeEvent.setOccurredAt(now);
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "APPOINTMENT_OUTCOME_CORRECTED", "Appointment",
					savedAppt.getId().toString(), "SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

			this.ownerHistoryService.recordOwnerHistory(savedAppt.getOwnerId(), savedAppt.getPetId(),
					savedAppt.getOriginatingRequestId(), "APPOINTMENT_OUTCOME_CORRECTED",
					"Appointment outcome corrected from " + previousOutcome + " to " + cmd.newOutcome(), null);

			log.info("Corrected outcome for appointment {} from {} to {}", savedAppt.getId(), previousOutcome,
					cmd.newOutcome());
			return savedOutcomeEvent;
		});
	}

}
