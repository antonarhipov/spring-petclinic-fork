package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OccupancyQueryService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityPrecedence;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicClosureRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicHours;
import org.springframework.samples.petclinic.scheduling.availability.ClinicHoursRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.availability.VetDateExceptionRepository;
import org.springframework.samples.petclinic.scheduling.availability.VetLeaveRepository;
import org.springframework.samples.petclinic.scheduling.availability.VetRecurringShiftRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;

@Component
public class SlotSelectionSnapshotFactory {

	private static final Logger logger = LoggerFactory.getLogger(SlotSelectionSnapshotFactory.class);

	private final SchedulingRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	private final AvailabilityRepository policies;

	private final ClinicHoursRepository clinicHours;

	private final VetRepository vets;

	private final VetRecurringShiftRepository vetRecurringShifts;

	private final VetDateExceptionRepository vetDateExceptions;

	private final VetLeaveRepository vetLeaves;

	private final ClinicClosureRepository clinicClosures;

	private final OccupancyQueryService occupancies;

	private final OfferRepository offers;

	private final CandidateSlotFactory candidates;

	private final Clock clock;

	public SlotSelectionSnapshotFactory(SchedulingRequestRepository requests, RequestRevisionRepository revisions,
			RequestWindowRepository windows, AvailabilityRepository policies, ClinicHoursRepository clinicHours,
			VetRepository vets, VetRecurringShiftRepository vetRecurringShifts,
			VetDateExceptionRepository vetDateExceptions, VetLeaveRepository vetLeaves,
			ClinicClosureRepository clinicClosures, OccupancyQueryService occupancies, OfferRepository offers,
			CandidateSlotFactory candidates, Clock clock) {
		this.requests = requests;
		this.revisions = revisions;
		this.windows = windows;
		this.policies = policies;
		this.clinicHours = clinicHours;
		this.vets = vets;
		this.vetRecurringShifts = vetRecurringShifts;
		this.vetDateExceptions = vetDateExceptions;
		this.vetLeaves = vetLeaves;
		this.clinicClosures = clinicClosures;
		this.occupancies = occupancies;
		this.offers = offers;
		this.candidates = candidates;
		this.clock = clock;
	}

	public SlotSelectionSnapshot create(Long requestId, MatchingMode mode) {
		SlotSelectionSnapshot withoutCandidates = createFacts(requestId, mode);
		List<CandidateSlot> enumerated = this.candidates.enumerate(withoutCandidates);
		logSnapshot(withoutCandidates, enumerated);
		return new SlotSelectionSnapshot(withoutCandidates.snapshotSchemaVersion(),
				withoutCandidates.solverConfigurationVersion(), withoutCandidates.requestId(),
				withoutCandidates.requestRevisionId(), withoutCandidates.requestRevisionVersion(),
				withoutCandidates.mode(), withoutCandidates.clinicZone(), withoutCandidates.now(),
				withoutCandidates.noticeBoundary(), withoutCandidates.horizonEnd(), withoutCandidates.durationMinutes(),
				withoutCandidates.gridMinutes(), withoutCandidates.configurationVersion(), withoutCandidates.petId(),
				withoutCandidates.requiredSpecialtyId(), withoutCandidates.preferredVeterinarianId(),
				withoutCandidates.veterinarianPreferenceStrength(), withoutCandidates.allowedWindows(),
				withoutCandidates.preferredWindows(), withoutCandidates.excludedWindows(),
				withoutCandidates.exclusionKeys(), withoutCandidates.veterinarians(), withoutCandidates.clinicHours(),
				withoutCandidates.veterinarianHours(), withoutCandidates.closures(), withoutCandidates.vetLeaves(),
				withoutCandidates.vetDateExceptions(), withoutCandidates.occupancies(), enumerated);
	}

	public SlotSelectionSnapshot createFacts(Long requestId, MatchingMode mode) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		Instant now = Instant.now(this.clock);
		Instant notice = now.plus(Duration.ofMinutes(policy.getOwnerMinimumNoticeMinutes()));
		Instant horizon = now.plus(Duration.ofDays(policy.getBookingHorizonDays()));
		List<RequestWindow> allWindows = this.windows.findByRequestRevisionId(revision.getId());
		List<TimeWindow> allowed = map(allWindows, "ALLOWED");
		List<TimeWindow> preferred = map(allWindows, "PREFERRED");
		List<TimeWindow> excluded = map(allWindows, "EXCLUDED");
		List<HoursFact> hours = this.clinicHours.findByPolicyId(policy.getId()).stream().map(this::toHours).toList();
		List<VetFact> vetFacts = this.vets.findAll().stream().map(this::toVet).toList();
		List<HoursFact> vetHours = vetFacts.stream()
			.flatMap(vet -> this.vetRecurringShifts.findByVeterinarianId(vet.veterinarianId())
				.stream()
				.map(shift -> new HoursFact(vet.veterinarianId(), shift.getDayOfWeek(), shift.getStartLocalTime(),
						shift.getEndLocalTime())))
			.toList();
		List<ClosureFact> closures = this.clinicClosures.findByPolicyId(policy.getId())
			.stream()
			.map(closure -> new ClosureFact(closure.getStartLocalDate(), closure.getEndLocalDate()))
			.toList();
		List<VetLeaveFact> vetLeaveFacts = vetFacts.stream()
			.flatMap(vet -> this.vetLeaves.findByVeterinarianId(vet.veterinarianId())
				.stream()
				.map(leave -> new VetLeaveFact(vet.veterinarianId(), leave.getStartLocalDate(),
						leave.getEndLocalDate())))
			.toList();
		List<VetDateExceptionFact> vetDateExceptionFacts = vetFacts.stream()
			.flatMap(vet -> this.vetDateExceptions.findByVeterinarianId(vet.veterinarianId())
				.stream()
				.map(exception -> new VetDateExceptionFact(vet.veterinarianId(), exception.getExceptionDate(),
						exception.getIntervals()
							.stream()
							.map(interval -> new AvailabilityPrecedence.Interval(interval.getStartLocalTime(),
									interval.getEndLocalTime()))
							.toList())))
			.toList();
		List<OccupancyFact> occupancy = this.occupancies.activeBlocks();
		Set<String> exclusionKeys = this.offers.findByRequestRevisionIdOrderByCreatedAtDesc(revision.getId())
			.stream()
			.filter(offer -> offer.getStatus() == OfferStatus.REJECTED || offer.getStatus() == OfferStatus.EXPIRED)
			.map(offer -> offer.getVeterinarianId() + "@" + offer.getStartAt())
			.collect(Collectors.toSet());
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", request.getId(), revision.getId(),
				revision.getVersion(), mode, policy.getZoneId(), now, notice, horizon, revision.getDurationMinutes(),
				policy.getGridMinutes(), policy.getConfigurationVersion(), request.getPetId(),
				revision.getSpecialtyId(), revision.getPreferredVeterinarianId(),
				revision.getVeterinarianPreferenceStrength(), allowed, preferred, excluded, exclusionKeys, vetFacts,
				hours, vetHours, closures, vetLeaveFacts, vetDateExceptionFacts, occupancy, List.of());
	}

	private void logSnapshot(SlotSelectionSnapshot facts, List<CandidateSlot> enumerated) {
		logger.info(
				"Solver input for requestId={} revisionId={} mode={}: candidateSlots={}, veterinarians={}, "
						+ "vetShiftRows={}, clinicHourRows={}, closures={}, vetLeaves={}, vetDateExceptions={}, "
						+ "activeOccupancies={}, excludedOfferKeys={}",
				facts.requestId(), facts.requestRevisionId(), facts.mode(), enumerated.size(),
				facts.veterinarians().size(), facts.veterinarianHours().size(), facts.clinicHours().size(),
				facts.closures().size(), facts.vetLeaves().size(), facts.vetDateExceptions().size(),
				facts.occupancies().size(), facts.exclusionKeys().size());
		logger.info(
				"Solver constraints for requestId={}: zone={}, durationMinutes={}, gridMinutes={}, "
						+ "noticeBoundary={}, horizonEnd={}, requiredSpecialtyId={}, preferredVeterinarianId={} ({}), "
						+ "allowedWindows={}, preferredWindows={}, excludedWindows={}",
				facts.requestId(), facts.clinicZone(), facts.durationMinutes(), facts.gridMinutes(),
				facts.noticeBoundary(), facts.horizonEnd(), facts.requiredSpecialtyId(),
				facts.preferredVeterinarianId(), facts.veterinarianPreferenceStrength(), facts.allowedWindows().size(),
				facts.preferredWindows().size(), facts.excludedWindows().size());
		if (enumerated.isEmpty()) {
			logger.warn(
					"Solver input for requestId={} has NO candidate slots: no suggestion is possible. Check "
							+ "veterinarian recurring shifts ({} rows), clinic hours ({} rows) and the request "
							+ "windows ({} allowed).",
					facts.requestId(), facts.veterinarianHours().size(), facts.clinicHours().size(),
					facts.allowedWindows().size());
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Solver candidate slots for requestId={}: {}", facts.requestId(),
					enumerated.stream()
						.limit(50)
						.map(slot -> slot.veterinarianId() + "@" + slot.startAt() + "/" + slot.preferenceClass())
						.toList());
		}
	}

	private List<TimeWindow> map(List<RequestWindow> source, String kind) {
		return source.stream()
			.filter(window -> kind.equals(window.getKind()))
			.map(window -> new TimeWindow(window.getStartAt(), window.getEndAt(), window.isFallbackAllowed()))
			.toList();
	}

	private HoursFact toHours(ClinicHours hours) {
		return new HoursFact(null, hours.getDayOfWeek(), hours.getStartLocalTime(), hours.getEndLocalTime());
	}

	private VetFact toVet(Vet vet) {
		Set<Integer> specialties = vet.getSpecialties()
			.stream()
			.map(Specialty::getId)
			.collect(Collectors.toCollection(HashSet::new));
		return new VetFact(vet.getId(), specialties);
	}

}
