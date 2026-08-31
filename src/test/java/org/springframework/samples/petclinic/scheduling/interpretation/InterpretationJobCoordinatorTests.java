package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetType;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.job.OutcomeCategory;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.vet.VetRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link InterpretationJobCoordinator} covering emergency detection, the
 * AI single-retry policy, successful interpretation persistence, and stale job
 * discarding.
 */
class InterpretationJobCoordinatorTests {

	private InterpretationClient interpretationClient;

	private ProtectedPayloadService payloadService;

	private AuditService auditService;

	private StaffFallbackPort staffFallbackPort;

	private BackgroundJobRepository jobRepository;

	private SchedulingRequestRepository requestRepository;

	private InterpretationRepository interpretationRepository;

	private WorkflowRevisionRepository workflowRevisionRepository;

	private ClinicPolicyRepository clinicPolicyRepository;

	private OwnerRepository ownerRepository;

	private VetRepository vetRepository;

	private InterpretationJobCoordinator coordinator;

	private ClinicPolicy policy;

	private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

	@BeforeEach
	void setUp() {
		this.interpretationClient = mock(InterpretationClient.class);
		this.payloadService = mock(ProtectedPayloadService.class);
		this.auditService = mock(AuditService.class);
		this.staffFallbackPort = mock(StaffFallbackPort.class);
		this.jobRepository = mock(BackgroundJobRepository.class);
		this.requestRepository = mock(SchedulingRequestRepository.class);
		this.interpretationRepository = mock(InterpretationRepository.class);
		this.workflowRevisionRepository = mock(WorkflowRevisionRepository.class);
		this.clinicPolicyRepository = mock(ClinicPolicyRepository.class);
		this.ownerRepository = mock(OwnerRepository.class);
		this.vetRepository = mock(VetRepository.class);

		this.policy = ClinicPolicy.createDefaultPolicy();
		given(this.clinicPolicyRepository.findSingleton()).willReturn(Optional.of(this.policy));
		given(this.ownerRepository.findById(any())).willReturn(Optional.of(sampleOwner()));
		given(this.vetRepository.findAll()).willReturn(List.of());
		given(this.vetRepository.findSpecialties()).willReturn(List.of());
		given(this.payloadService.encrypt(any())).willReturn(new ProtectedPayload());

		this.coordinator = new InterpretationJobCoordinator(this.interpretationClient,
				new InterpretationOutputValidator(), new EmergencyKeywordScreen(), this.payloadService,
				this.auditService, this.staffFallbackPort, this.jobRepository, this.requestRepository,
				this.interpretationRepository, this.workflowRevisionRepository, this.clinicPolicyRepository,
				this.ownerRepository, this.vetRepository, new ObjectMapper(), this.clock);
	}

	private Owner sampleOwner() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		pet.setId(1);
		pet.setName("Leo");
		PetType cat = new PetType();
		cat.setName("cat");
		pet.setType(cat);
		owner.addPet(pet);
		return owner;
	}

	private TextRevision textRevisionFor(SchedulingRequest request, Long id) {
		TextRevision textRevision = new TextRevision(request, 1, Instant.now(), new ProtectedPayload(),
				new ProtectedPayload());
		textRevision.setId(id);
		return textRevision;
	}

	private BackgroundJob jobFor(TextRevision textRevision) {
		BackgroundJob job = new BackgroundJob(JobType.INTERPRETATION, textRevision, null, JobState.RUNNING,
				Instant.now());
		job.setId(1L);
		return job;
	}

	@Test
	void emergencyProseImmediatelyRoutesToStaffHandling() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 10L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);

		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("My dog is bleeding heavily and needs help");

		this.coordinator.executeInterpretationJob(job);

		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.EMERGENCY_DETECTED);
		verify(this.staffFallbackPort).sendToFallbackQueue(eq(request.getId()), eq("EMERGENCY_DETECTED"),
				eq(Urgency.EMERGENCY_SUSPECTED), any());
		verify(this.interpretationClient, times(0)).interpret(any());
	}

	@Test
	void successfulFirstAttemptMarksRequestAwaitingReview() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 11L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);

		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("Annual vaccination for Leo next Tuesday morning");

		InterpretationCandidate candidate = new InterpretationCandidate("1", "Annual vaccination", Urgency.ROUTINE,
				List.of(), 30, null, null, List.of(), List.of(), List.of());
		given(this.interpretationClient.interpret(any())).willReturn(candidate);

		this.coordinator.executeInterpretationJob(job);

		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.SUCCESS);
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);
		verify(this.workflowRevisionRepository).save(any(WorkflowRevision.class));
		verify(this.staffFallbackPort, times(0)).sendToFallbackQueue(any(), any(), any(), any());
	}

	@Test
	void invalidFirstAttemptRetriesThenSucceeds() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 12L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);

		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("Checkup for Leo");

		InterpretationCandidate invalidCandidate = new InterpretationCandidate("bad-version", "Checkup",
				Urgency.ROUTINE, List.of(), 30, null, null, List.of(), List.of(), List.of());
		InterpretationCandidate validCandidate = new InterpretationCandidate("1", "Checkup", Urgency.ROUTINE, List.of(),
				30, null, null, List.of(), List.of(), List.of());
		given(this.interpretationClient.interpret(any())).willReturn(invalidCandidate).willReturn(validCandidate);

		this.coordinator.executeInterpretationJob(job);

		assertThat(job.getAttemptCount()).isEqualTo(2);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.SUCCESS);
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);
	}

	@Test
	void bothAttemptsUnusableRoutesToStaffQueue() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 13L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);

		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("Checkup for Leo");
		given(this.interpretationClient.interpret(any())).willThrow(new RuntimeException("Ollama timeout"));

		this.coordinator.executeInterpretationJob(job);

		assertThat(job.getAttemptCount()).isEqualTo(2);
		assertThat(job.getState()).isEqualTo(JobState.FAILED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.UNUSABLE_OUTPUT);
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(this.staffFallbackPort).sendToFallbackQueue(eq(request.getId()), eq("UNUSABLE_OUTPUT"),
				eq(Urgency.ROUTINE), any());
	}

	@Test
	void configuredDeadlineStopsAStillRunningInterpretation() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 14L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);

		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("Checkup for Leo");
		given(this.interpretationClient.interpret(any())).willAnswer(invocation -> {
			Thread.sleep(5_000);
			return null;
		});
		InterpretationJobCoordinator shortDeadlineCoordinator = new InterpretationJobCoordinator(
				this.interpretationClient, new InterpretationOutputValidator(), new EmergencyKeywordScreen(),
				this.payloadService, this.auditService, this.staffFallbackPort, this.jobRepository,
				this.requestRepository, this.interpretationRepository, this.workflowRevisionRepository,
				this.clinicPolicyRepository, this.ownerRepository, this.vetRepository, new ObjectMapper(), this.clock,
				Duration.ofMillis(50));

		shortDeadlineCoordinator.executeInterpretationJob(job);

		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.UNUSABLE_OUTPUT);
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(this.interpretationClient, times(1)).interpret(any());
	}

	@Test
	void staleJobIsDiscardedWithoutCallingAi() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision currentTextRevision = textRevisionFor(request, 21L);
		TextRevision staleTextRevision = textRevisionFor(request, 20L);
		request.setCurrentTextRevision(currentTextRevision);
		BackgroundJob staleJob = jobFor(staleTextRevision);

		this.coordinator.executeInterpretationJob(staleJob);

		assertThat(staleJob.getState()).isEqualTo(JobState.STALE);
		verify(this.interpretationClient, times(0)).interpret(any());
		verify(this.staffFallbackPort, times(0)).sendToFallbackQueue(any(), any(), any(), any());
	}

	@Test
	void resultCommitIsIgnoredWhenLeaseChangesAfterAiReturns() {
		SchedulingRequest request = new SchedulingRequest(1, 1, RequestState.AWAITING_INTERPRETATION, Instant.now());
		request.setId(1L);
		TextRevision textRevision = textRevisionFor(request, 30L);
		request.setCurrentTextRevision(textRevision);
		BackgroundJob job = jobFor(textRevision);
		job.setLeaseToken("original-lease");
		given(this.jobRepository.findById(job.getId())).willReturn(Optional.of(job));
		given(this.payloadService.decrypt(eq(textRevision.getProsePayload()), eq(String.class)))
			.willReturn("Routine vaccination for Leo");
		InterpretationCandidate candidate = new InterpretationCandidate("1", "Vaccination", Urgency.ROUTINE, List.of(),
				30, null, null, List.of(), List.of(), List.of());
		given(this.interpretationClient.interpret(any())).willAnswer(invocation -> {
			job.setLeaseToken("replacement-lease");
			return candidate;
		});

		this.coordinator.executeInterpretationJob(job.getId(), "original-lease");

		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_INTERPRETATION);
		verify(this.workflowRevisionRepository, never()).save(any());
		verify(this.staffFallbackPort, never()).sendToFallbackQueue(any(), any(), any(), any());
	}

}
