package org.springframework.samples.petclinic.scheduling.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.exhaustivesearch.ExhaustiveSearchPhaseConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.AvailabilityWindowKind;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.service.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;

@ExtendWith(MockitoExtension.class)
class SchedulingProblemFactoryOwnerWindowTests {

	private static final Instant NOW = Instant.parse("2026-08-27T10:33:05Z");

	@Mock
	private VetRepository vetRepository;

	@Mock
	private AppointmentRepository appointmentRepository;

	@Mock
	private ReservationRepository reservationRepository;

	@Mock
	private RejectionRepository rejectionRepository;

	@Mock
	private RequestAvailabilityIntervalRepository intervalRepository;

	@Mock
	private EffectiveAvailabilityService effectiveAvailabilityService;

	private SchedulingProblemFactory factory;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		SchedulingTimeService timeService = new SchedulingTimeService(Clock.fixed(NOW, ZoneId.of("UTC")),
				ZoneId.of("UTC"));
		factory = new SchedulingProblemFactory(vetRepository, appointmentRepository, reservationRepository,
				rejectionRepository, intervalRepository, effectiveAvailabilityService, timeService);

		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(10);
		Vet vet = new Vet();
		vet.setId(7);
		vet.setActive(true);

		request = new SchedulingRequest();
		request.setId(100);
		request.setOwner(owner);
		request.setPet(pet);
		request.setCareType(CareType.GENERAL);
		request.setDurationMinutes(30);
		request.setOwnerHorizonEnd(Instant.parse("2026-09-07T00:00:00Z"));

		when(vetRepository.findAll()).thenReturn(List.of(vet));
		when(appointmentRepository.findAll()).thenReturn(List.of());
		when(reservationRepository.findAll()).thenReturn(List.of());
		when(rejectionRepository.findByRequestId(100)).thenReturn(List.of());
		when(effectiveAvailabilityService.calculateEffectiveAvailability(eq(7), any(), any())).thenReturn(List.of(
				new TimeInterval(Instant.parse("2026-09-03T08:00:00Z"), Instant.parse("2026-09-03T20:00:00Z")),
				new TimeInterval(Instant.parse("2026-09-05T12:00:00Z"), Instant.parse("2026-09-05T17:00:00Z"))));
	}

	@Test
	void derivesHardAndPreferredFlagsFromMaterializedIntervals() {
		when(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(100)).thenReturn(
				List.of(interval(AvailabilityWindowKind.ALLOWED, "2026-09-03T12:00:00Z", "2026-09-03T17:00:00Z"),
						interval(AvailabilityWindowKind.PREFERRED, "2026-09-03T13:00:00Z", "2026-09-03T15:00:00Z"),
						interval(AvailabilityWindowKind.EXCLUDED, "2026-09-03T14:00:00Z", "2026-09-03T14:30:00Z")));

		SchedulingProblem problem = factory.createProblem(request, new ClinicSettings(), NOW);

		CandidateSlot currentMorning = candidate(problem, "2026-08-27T10:45:00Z");
		CandidateSlot allowedAfternoon = candidate(problem, "2026-09-03T13:00:00Z");
		CandidateSlot excludedOverlap = candidate(problem, "2026-09-03T13:45:00Z");
		assertThat(currentMorning.isMatchesAllowedWindows()).isFalse();
		assertThat(allowedAfternoon.isMatchesAllowedWindows()).isTrue();
		assertThat(allowedAfternoon.isPreferredWindow()).isTrue();
		assertThat(excludedOverlap.isMatchesExcludedWindows()).isTrue();
	}

	@Test
	void originalExampleSolvesToNextThursdayAfternoonInsteadOfCurrentMorning() {
		when(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(100)).thenReturn(
				List.of(interval(AvailabilityWindowKind.ALLOWED, "2026-09-03T12:00:00Z", "2026-09-03T17:00:00Z")));
		SchedulingProblem problem = factory.createProblem(request, new ClinicSettings(), NOW);
		SolverConfig solverConfig = new SolverConfig().withSolutionClass(SchedulingProblem.class)
			.withEntityClasses(ProposedAssignment.class)
			.withConstraintProviderClass(AppointmentPlanningConstraintProvider.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(2)))
			.withPhaseList(List.of(new ExhaustiveSearchPhaseConfig()));

		SchedulingProblem solution = SolverFactory.<SchedulingProblem>create(solverConfig).buildSolver().solve(problem);

		assertThat(solution.getScore().hardScore(0)).isZero();
		assertThat(solution.getProposedAssignment().getCandidate().getStartInstant())
			.isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
	}

	@Test
	void weekendEligibilityStillFollowsEffectiveVeterinarianAvailability() {
		when(intervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(100)).thenReturn(
				List.of(interval(AvailabilityWindowKind.ALLOWED, "2026-09-03T00:00:00Z", "2026-09-07T00:00:00Z")));

		SchedulingProblem problem = factory.createProblem(request, new ClinicSettings(), NOW);

		assertThat(candidate(problem, "2026-09-05T10:00:00Z").isInContiguousAvailability()).isFalse();
		assertThat(candidate(problem, "2026-09-05T12:00:00Z").isInContiguousAvailability()).isTrue();
	}

	private CandidateSlot candidate(SchedulingProblem problem, String start) {
		Instant instant = Instant.parse(start);
		return problem.getCandidateList()
			.stream()
			.filter(candidate -> candidate.getStartInstant().equals(instant))
			.findFirst()
			.orElseThrow();
	}

	private RequestAvailabilityInterval interval(AvailabilityWindowKind kind, String start, String end) {
		RequestAvailabilityInterval interval = new RequestAvailabilityInterval();
		interval.setKind(kind);
		interval.setStartInstant(Instant.parse(start));
		interval.setEndInstant(Instant.parse(end));
		return interval;
	}

}
