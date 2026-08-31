package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.availability.CalendarState;
import org.springframework.samples.petclinic.availability.CalendarStateRepository;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchingSnapshotFactory {

	private static final Logger log = LoggerFactory.getLogger(MatchingSnapshotFactory.class);

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final CapacityConflictService capacityConflictService;

	private final CalendarStateRepository calendarStateRepository;

	private final ClinicPolicyRepository clinicPolicyRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final OfferExclusionRepository offerExclusionRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public MatchingSnapshotFactory(EffectiveAvailabilityService effectiveAvailabilityService,
			CapacityConflictService capacityConflictService, CalendarStateRepository calendarStateRepository,
			ClinicPolicyRepository clinicPolicyRepository, WorkflowRevisionRepository workflowRevisionRepository,
			OfferExclusionRepository offerExclusionRepository, VetRepository vetRepository, Clock clock) {
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.capacityConflictService = capacityConflictService;
		this.calendarStateRepository = calendarStateRepository;
		this.clinicPolicyRepository = clinicPolicyRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.offerExclusionRepository = offerExclusionRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public SnapshotResult buildSnapshot(WorkflowRevision workflowRevision) {
		WorkflowRevision targetWf = (workflowRevision != null && workflowRevision.getId() != null)
				? this.workflowRevisionRepository.findById(workflowRevision.getId()).orElse(workflowRevision)
				: workflowRevision;

		CalendarState calendarState = this.calendarStateRepository.findById(1).orElse(null);
		long calendarRevision = (calendarState != null) ? calendarState.getRevision() : 0L;

		ClinicPolicy policy = this.clinicPolicyRepository.findSingleton().orElseGet(ClinicPolicy::createDefaultPolicy);
		ZoneId zoneId = policy.getTimeZone();

		Instant now = this.clock.instant();
		ZonedDateTime zdtNow = now.atZone(zoneId);
		LocalDate startDate = zdtNow.toLocalDate().plusDays(1);
		LocalDate endDate = startDate.plusDays(policy.getBookingHorizonDays());

		List<Vet> eligibleVets = findEligibleVets(targetWf.getRequiredSpecialtyId());
		int durationMinutes = targetWf.getDurationMinutes();

		List<AvailabilityWindow> windows = targetWf.getAvailabilityWindows();
		List<AvailabilityWindow> allowedWindows = windows.stream()
			.filter(w -> w.getClassification() == WindowClassification.ALLOWED)
			.toList();
		List<AvailabilityWindow> preferredWindows = windows.stream()
			.filter(w -> w.getClassification() == WindowClassification.PREFERRED)
			.toList();
		List<AvailabilityWindow> fallbackWindows = windows.stream()
			.filter(w -> w.getClassification() == WindowClassification.FALLBACK)
			.toList();

		List<OfferExclusion> exclusions = (targetWf.getId() != null)
				? this.offerExclusionRepository.findByWorkflowRevisionId(targetWf.getId()) : targetWf.getExclusions();

		List<CandidateSlot> candidates = new ArrayList<>();

		for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
			final LocalDate currentDate = date;
			for (Vet vet : eligibleVets) {
				List<TimeInterval> effectiveIntervals = this.effectiveAvailabilityService
					.findEffectiveAvailability(vet.getId(), currentDate);

				for (TimeInterval interval : effectiveIntervals) {
					ZonedDateTime intervalStart = interval.getStartAt().atZone(zoneId);
					ZonedDateTime intervalEnd = interval.getEndAt().atZone(zoneId);

					ZonedDateTime slotStart = intervalStart;
					while (!slotStart.plusMinutes(durationMinutes).isAfter(intervalEnd)) {
						ZonedDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
						Instant slotStartInstant = slotStart.toInstant();
						Instant slotEndInstant = slotEnd.toInstant();

						if (isAllowedByWindows(allowedWindows, currentDate, slotStart.toLocalTime(),
								slotEnd.toLocalTime())
								&& !isExcluded(exclusions, vet.getId(), slotStartInstant, slotEndInstant)) {

							boolean conflict = this.capacityConflictService.hasOverlappingBlocker(vet.getId(),
									targetWf.getRequest().getPetId(), targetWf.getRequest().getOwnerId(),
									slotStartInstant, slotEndInstant);

							if (!conflict) {
								boolean inPreferred = isMatchedByWindows(preferredWindows, currentDate,
										slotStart.toLocalTime(), slotEnd.toLocalTime());
								boolean inFallback = isMatchedByWindows(fallbackWindows, currentDate,
										slotStart.toLocalTime(), slotEnd.toLocalTime());
								boolean preferredVet = (targetWf.getPreferredVetId() != null
										&& targetWf.getPreferredVetId().equals(vet.getId()));
								int minutesFromRef = (int) Duration.between(now, slotStartInstant).toMinutes();
								int gapMinutes = calculateGapMinutes(vet.getId(), slotStartInstant, slotEndInstant);
								String tieBreakKey = vet.getId() + ":" + slotStartInstant;

								candidates.add(new CandidateSlot(vet.getId(),
										vet.getFirstName() + " " + vet.getLastName(), slotStartInstant, slotEndInstant,
										zoneId.getId(), durationMinutes, inPreferred, preferredVet, inFallback,
										minutesFromRef, gapMinutes, tieBreakKey));
							}
						}

						slotStart = slotStart.plusMinutes(15);
					}
				}
			}
		}

		log.info("Built snapshot for workflow revision {} with {} candidates (calendar revision {})", targetWf.getId(),
				candidates.size(), calendarRevision);
		return new SnapshotResult(calendarRevision, candidates);
	}

	private List<Vet> findEligibleVets(Integer requiredSpecialtyId) {
		List<Vet> allVets = this.vetRepository.findAll();
		if (requiredSpecialtyId == null) {
			return allVets;
		}
		return allVets.stream()
			.filter(vet -> vet.getSpecialties().stream().map(Specialty::getId).anyMatch(requiredSpecialtyId::equals))
			.collect(Collectors.toList());
	}

	private boolean isAllowedByWindows(List<AvailabilityWindow> allowedWindows, LocalDate date, LocalTime start,
			LocalTime end) {
		if (allowedWindows == null || allowedWindows.isEmpty()) {
			return true;
		}
		return isMatchedByWindows(allowedWindows, date, start, end);
	}

	private boolean isMatchedByWindows(List<AvailabilityWindow> windows, LocalDate date, LocalTime start,
			LocalTime end) {
		if (windows == null || windows.isEmpty()) {
			return false;
		}
		for (AvailabilityWindow window : windows) {
			if (window.getShape() == WindowShape.ONE_OFF) {
				if (date.equals(window.getLocalDate())) {
					if (!start.isBefore(window.getStartTime()) && !end.isAfter(window.getEndTime())) {
						return true;
					}
				}
			}
			else if (window.getShape() == WindowShape.WEEKLY) {
				if (!date.isBefore(window.getRangeStart()) && !date.isAfter(window.getRangeEnd())) {
					String weekdays = window.getWeekdays();
					if (weekdays != null && weekdays.contains(date.getDayOfWeek().name())) {
						if (!start.isBefore(window.getStartTime()) && !end.isAfter(window.getEndTime())) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private boolean isExcluded(List<OfferExclusion> exclusions, Integer vetId, Instant startAt, Instant endAt) {
		if (exclusions == null || exclusions.isEmpty()) {
			return false;
		}
		return exclusions.stream()
			.anyMatch(ex -> ex.getVetId().equals(vetId) && ex.getStartAt().equals(startAt)
					&& ex.getEndAt().equals(endAt));
	}

	private int calculateGapMinutes(Integer vetId, Instant startAt, Instant endAt) {
		// Default 0 for optimal gap when no adjacent blockers found
		return 0;
	}

	public record SnapshotResult(long calendarRevision, List<CandidateSlot> candidateSlots) {
	}

}
