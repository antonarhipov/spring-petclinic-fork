package org.springframework.samples.petclinic.scheduling;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.RecurringShift;
import org.springframework.samples.petclinic.availability.RecurringShiftRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationCandidate;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationClient;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobWorker;
import org.springframework.samples.petclinic.scheduling.matching.AppointmentSchedulingSolver;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.MatchingSnapshotFactory;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.request.OwnerInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.support.SchedulingTestFixtures;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "petclinic.jobs.enabled=false" })
@Transactional
public class SchedulingPerformanceAcceptanceTests {

	private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Amsterdam");

	@MockitoBean
	private InterpretationClient interpretationClient;

	@Autowired
	private BackgroundJobWorker backgroundJobWorker;

	@Autowired
	private SchedulingRequestService schedulingRequestService;

	@Autowired
	private OwnerInterpretationService ownerInterpretationService;

	@Autowired
	private MatchingSnapshotFactory snapshotFactory;

	@Autowired
	private AppointmentSchedulingSolver solver;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private RecurringShiftRepository recurringShiftRepository;

	@Autowired
	private ClinicPolicyRepository clinicPolicyRepository;

	private Owner owner;

	private Pet pet;

	private Vet vet;

	@BeforeEach
	void setUpFixtures() {
		this.owner = this.ownerRepository.findById(1).orElseThrow();
		this.pet = this.owner.getPets().get(0);
		this.vet = this.vetRepository.findById(1).orElseThrow();

		if (this.clinicPolicyRepository.findById(1).isEmpty()) {
			ClinicPolicy policy = ClinicPolicy.createDefaultPolicy();
			policy.setId(1);
			policy.setZoneId(CLINIC_ZONE.getId());
			this.clinicPolicyRepository.save(policy);
		}

		if (this.recurringShiftRepository.count() == 0) {
			for (DayOfWeek day : DayOfWeek.values()) {
				if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
					this.recurringShiftRepository
						.save(new RecurringShift(this.vet.getId(), day, LocalTime.of(9, 0), LocalTime.of(17, 0)));
				}
			}
		}
	}

	private LocalDate nextWeekday(int weekdayOffset) {
		LocalDate date = LocalDate.now(CLINIC_ZONE);
		int added = 0;
		while (added < weekdayOffset) {
			date = date.plusDays(1);
			if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
				added++;
			}
		}
		return date;
	}

	@Test
	@DisplayName("Deterministic Timefold solver matching completes in under 5 seconds")
	void deterministicSolverCompletesUnderFiveSeconds() {
		LocalDate targetDate = nextWeekday(2);
		InterpretationCandidate candidate = SchedulingTestFixtures.routineCandidate(targetDate, LocalTime.of(9, 0),
				LocalTime.of(17, 0), 30);
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine checkup for performance test", true);
		this.backgroundJobWorker.processNextJob();

		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		SchedulingRequest reloaded = this.requestRepository.findById(request.getId()).orElseThrow();
		WorkflowRevision workflowRev = reloaded.getCurrentWorkflowRevision();

		MatchingSnapshotFactory.SnapshotResult snapshot = this.snapshotFactory.buildSnapshot(workflowRev);
		assertThat(snapshot.candidateSlots()).isNotEmpty();

		long startTime = System.nanoTime();
		Optional<CandidateSlot> chosenSlot = this.solver.solve(snapshot.candidateSlots());
		long durationMs = (System.nanoTime() - startTime) / 1_000_000;

		assertThat(chosenSlot).isPresent();
		assertThat(durationMs).isLessThan(5_000L);
	}

	@Test
	@DisplayName("Full smart scheduling workflow completes well within the 20 second budget")
	void smartSchedulingWorkflowCompletesWithinTwentySeconds() {
		LocalDate targetDate = nextWeekday(3);
		InterpretationCandidate candidate = SchedulingTestFixtures.routineCandidate(targetDate, LocalTime.of(10, 0),
				LocalTime.of(15, 0), 30);
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		long startTime = System.nanoTime();

		// Step 1: Owner submission
		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Routine wellness check", true);

		// Step 2: Interpretation processing
		this.backgroundJobWorker.processNextJob();

		// Step 3: Owner confirmation
		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());

		// Step 4: Matching processing
		this.backgroundJobWorker.processNextJob();

		long durationMs = (System.nanoTime() - startTime) / 1_000_000;

		Offer offer = this.offerRepository.findByRequestIdAndState(request.getId(), OfferState.HELD).orElseThrow();
		assertThat(offer.getState()).isEqualTo(OfferState.HELD);
		assertThat(durationMs).isLessThan(20_000L);
	}

	@Test
	@DisplayName("Fallback workflow for non-matching requests completes within budget")
	void fallbackWorkflowCompletesWithinTwentySeconds() {
		LocalDate targetDate = nextWeekday(4);
		org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate window = new org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate(
				org.springframework.samples.petclinic.scheduling.request.WindowClassification.ALLOWED,
				org.springframework.samples.petclinic.scheduling.request.WindowShape.ONE_OFF, targetDate, null, null,
				null, LocalTime.of(22, 0), LocalTime.of(23, 0), "Late night", null);
		InterpretationCandidate candidate = new InterpretationCandidate("1", "Night visit",
				org.springframework.samples.petclinic.scheduling.interpretation.Urgency.ROUTINE, List.of(), 30, null,
				null, List.of(window), List.of(), List.of());
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		long startTime = System.nanoTime();

		SchedulingRequest request = this.schedulingRequestService.submitRequest(this.owner.getId(), this.pet.getId(),
				"Night appointment", true);
		this.backgroundJobWorker.processNextJob();
		this.ownerInterpretationService.confirmInterpretation(request.getId(), this.owner.getId());
		this.backgroundJobWorker.processNextJob();

		long durationMs = (System.nanoTime() - startTime) / 1_000_000;

		SchedulingRequest finished = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(finished.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(durationMs).isLessThan(20_000L);
	}

}
