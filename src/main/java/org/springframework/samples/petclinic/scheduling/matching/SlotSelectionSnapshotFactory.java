package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OccupancyQueryService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicHours;
import org.springframework.samples.petclinic.scheduling.availability.ClinicHoursRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
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

	private final SchedulingRequestRepository requests;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	private final AvailabilityRepository policies;

	private final ClinicHoursRepository clinicHours;

	private final VetRepository vets;

	private final OccupancyQueryService occupancies;

	private final OfferRepository offers;

	private final CandidateSlotFactory candidates;

	private final Clock clock;

	public SlotSelectionSnapshotFactory(SchedulingRequestRepository requests, RequestRevisionRepository revisions,
			RequestWindowRepository windows, AvailabilityRepository policies, ClinicHoursRepository clinicHours,
			VetRepository vets, OccupancyQueryService occupancies, OfferRepository offers,
			CandidateSlotFactory candidates, Clock clock) {
		this.requests = requests;
		this.revisions = revisions;
		this.windows = windows;
		this.policies = policies;
		this.clinicHours = clinicHours;
		this.vets = vets;
		this.occupancies = occupancies;
		this.offers = offers;
		this.candidates = candidates;
		this.clock = clock;
	}

	public SlotSelectionSnapshot create(Long requestId, MatchingMode mode) {
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
		List<OccupancyFact> occupancy = this.occupancies.activeBlocks();
		Set<String> exclusionKeys = this.offers.findByRequestRevisionIdOrderByCreatedAtDesc(revision.getId())
			.stream()
			.filter(offer -> offer.getStatus() == OfferStatus.REJECTED || offer.getStatus() == OfferStatus.EXPIRED)
			.map(offer -> offer.getVeterinarianId() + "@" + offer.getStartAt())
			.collect(Collectors.toSet());
		SlotSelectionSnapshot withoutCandidates = new SlotSelectionSnapshot("1.0", "slot-selection-1", request.getId(),
				revision.getId(), revision.getVersion(), mode, policy.getZoneId(), now, notice, horizon,
				revision.getDurationMinutes(), policy.getGridMinutes(), policy.getConfigurationVersion(),
				request.getPetId(), revision.getSpecialtyId(), revision.getPreferredVeterinarianId(),
				revision.getVeterinarianPreferenceStrength(), allowed, preferred, excluded, exclusionKeys, vetFacts,
				hours, List.of(), occupancy, List.of());
		List<CandidateSlot> enumerated = this.candidates.enumerate(withoutCandidates);
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
				withoutCandidates.veterinarianHours(), withoutCandidates.occupancies(), enumerated);
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
