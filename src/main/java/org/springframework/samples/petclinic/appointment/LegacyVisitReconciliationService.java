package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitProvenance;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LegacyVisitReconciliationService {

	private static final Logger log = LoggerFactory.getLogger(LegacyVisitReconciliationService.class);

	private final VisitRepository visitRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository changeEventRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final CapacityConflictService capacityConflictService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final ProtectedPayloadService payloadService;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public LegacyVisitReconciliationService(VisitRepository visitRepository, OwnerRepository ownerRepository,
			VetRepository vetRepository, AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository changeEventRepository, CalendarMutationCoordinator calendarCoordinator,
			CapacityConflictService capacityConflictService, EffectiveAvailabilityService effectiveAvailabilityService,
			ProtectedPayloadService payloadService, OwnerHistoryService ownerHistoryService, AuditService auditService,
			Clock clock) {
		this.visitRepository = visitRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.appointmentRepository = appointmentRepository;
		this.changeEventRepository = changeEventRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.capacityConflictService = capacityConflictService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.payloadService = payloadService;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public record LegacyVisitSummaryDto(Integer visitId, Integer petId, String petName, Integer ownerId,
			String ownerName, LocalDate visitDate, String description) {
	}

	public record ReconcileLegacyVisitCommand(Integer visitId, Long actorAccountId, Integer vetId, Instant startAt,
			Instant endAt, boolean ownerAgreementRecorded, String agreementMedium, String internalReason) {
	}

	@Transactional(readOnly = true)
	public List<LegacyVisitSummaryDto> findUnreconciledFutureLegacyVisits() {
		LocalDate today = LocalDate.now(this.clock);
		List<Visit> visits = this.visitRepository.findFutureLegacyVisits(today);

		return visits.stream().map(visit -> {
			Integer petId = visit.getPetId();
			String petName = "Pet";
			Integer ownerId = null;
			String ownerName = "Unknown";

			if (petId != null) {
				Owner owner = this.ownerRepository.findByPetId(petId).orElse(null);
				if (owner != null) {
					ownerId = owner.getId();
					ownerName = owner.getFirstName() + " " + owner.getLastName();
					Pet pet = owner.getPet(petId);
					if (pet != null) {
						petName = pet.getName();
					}
				}
			}

			return new LegacyVisitSummaryDto(visit.getId(), petId, petName, ownerId, ownerName, visit.getDate(),
					visit.getDescription());
		}).toList();
	}

	public Appointment reconcileLegacyVisit(ReconcileLegacyVisitCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.visitId(), "visitId must not be null");
		Objects.requireNonNull(cmd.actorAccountId(), "actorAccountId must not be null");
		Objects.requireNonNull(cmd.vetId(), "vetId must not be null");
		Objects.requireNonNull(cmd.startAt(), "startAt must not be null");
		Objects.requireNonNull(cmd.endAt(), "endAt must not be null");

		if (!cmd.ownerAgreementRecorded()) {
			throw new IllegalArgumentException("Owner agreement is mandatory for reconciling a legacy visit");
		}
		if (cmd.agreementMedium() == null || cmd.agreementMedium().isBlank()) {
			throw new IllegalArgumentException("Agreement medium is required");
		}
		if (cmd.internalReason() == null || cmd.internalReason().isBlank()) {
			throw new IllegalArgumentException("Internal reason is required");
		}

		Visit visit = this.visitRepository.findById(cmd.visitId())
			.orElseThrow(() -> new IllegalArgumentException("Visit not found: " + cmd.visitId()));

		if (visit.getProvenance() != VisitProvenance.LEGACY) {
			throw new IllegalStateException("Only legacy visits can be reconciled");
		}
		if (visit.getAppointmentId() != null) {
			throw new IllegalStateException("Visit is already reconciled to appointment: " + visit.getAppointmentId());
		}

		Integer petId = visit.getPetId();
		if (petId == null) {
			throw new IllegalStateException("Legacy visit has no associated pet");
		}

		Owner owner = this.ownerRepository.findByPetId(petId)
			.orElseThrow(() -> new IllegalStateException("No owner found for pet " + petId));

		Vet vet = this.vetRepository.findById(cmd.vetId())
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + cmd.vetId()));

		return this.calendarCoordinator.executeWithLock(() -> {
			Instant now = this.clock.instant();
			if (this.capacityConflictService.hasOverlappingBlocker(cmd.vetId(), petId, owner.getId(), cmd.startAt(),
					cmd.endAt())) {
				throw new AvailabilityConflictException("Selected appointment slot has a capacity conflict");
			}

			String zoneIdStr = this.effectiveAvailabilityService.getClinicPolicy().getZoneId();
			Appointment appointment = new Appointment(owner.getId(), petId, cmd.vetId(), cmd.startAt(), cmd.endAt(),
					zoneIdStr);
			appointment.setLegacyVisitId(visit.getId());
			appointment.setBookingState(BookingState.CONFIRMED);
			appointment.setOutcomeState(OutcomeState.PENDING);
			Appointment savedAppt = this.appointmentRepository.save(appointment);

			visit.setAppointmentId(savedAppt.getId());
			visit.setVetId(cmd.vetId());
			this.visitRepository.save(visit);

			ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "RECONCILIATION_REASON", 1,
					"text/plain", cmd.internalReason().trim());

			AppointmentChangeEvent changeEvent = new AppointmentChangeEvent(savedAppt.getId(), cmd.actorAccountId(),
					"ROLE_STAFF", "LEGACY_RECONCILED", true, cmd.agreementMedium().trim());
			changeEvent.setOccurredAt(now);
			changeEvent.setProtectedReasonPayloadId(reasonPayload.getId());
			this.changeEventRepository.save(changeEvent);

			this.auditService.recordEvent(cmd.actorAccountId(), "LEGACY_VISIT_RECONCILED", "Appointment",
					savedAppt.getId().toString(), "SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

			this.ownerHistoryService.recordOwnerHistory(owner.getId(), petId, null, "LEGACY_RECONCILED",
					"Legacy visit on " + visit.getDate() + " reconciled to scheduled appointment with Dr. "
							+ vet.getLastName(),
					null);

			log.info("Reconciled legacy visit {} into appointment {}", visit.getId(), savedAppt.getId());
			return savedAppt;
		});
	}

}
