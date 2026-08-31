package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.appointment.AppointmentChangeEventRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.availability.CalendarStateRepository;
import org.springframework.samples.petclinic.availability.CapacityConflictService;
import org.springframework.samples.petclinic.availability.ClinicPolicyRepository;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.job.OutcomeCategory;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class MatchingCoordinatorConcurrencyTests {

	@Autowired
	private MatchingJobCoordinator matchingJobCoordinator;

	@Autowired
	private MatchingSnapshotFactory matchingSnapshotFactory;

	@Autowired
	private AppointmentSchedulingSolver solver;

	@Autowired
	private OfferService offerService;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private ActiveSchedulingRequestRepository activeRequestRepository;

	@Autowired
	private BackgroundJobRepository jobRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentChangeEventRepository changeEventRepository;

	@Autowired
	private CalendarMutationCoordinator calendarCoordinator;

	@Autowired
	private CapacityConflictService capacityConflictService;

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private Clock clock;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		this.executorService = Executors.newFixedThreadPool(4);
		cleanupData();
	}

	@AfterEach
	void tearDown() {
		this.executorService.shutdown();
		cleanupData();
	}

	private void cleanupData() {
		this.offerRepository.deleteAll();
		this.jobRepository.deleteAll();
		this.activeRequestRepository.deleteAll();
		for (SchedulingRequest req : this.requestRepository.findAll()) {
			req.setCurrentWorkflowRevision(null);
			req.setCurrentTextRevision(null);
			this.requestRepository.save(req);
		}
		this.workflowRevisionRepository.deleteAll();
		this.requestRepository.deleteAll();
		this.changeEventRepository.deleteAll();
		this.appointmentRepository.deleteAll();
	}

	private SchedulingRequest createRequest(int ownerId, int petId, RequestState state) {
		return new SchedulingRequest(ownerId, petId, state, this.clock.instant());
	}

	private WorkflowRevision createWorkflowRevision(SchedulingRequest req, int revNum, int duration, Urgency urgency,
			Integer preferredVetId) {
		ProtectedPayload reason = (this.payloadService != null) ? this.payloadService.encrypt("Routine checkup")
				: new ProtectedPayload();
		return new WorkflowRevision(req, revNum, null, null, WorkflowRevisionState.CONFIRMED, reason, duration,
				preferredVetId, null, urgency, 0);
	}

	private BackgroundJob createMatchingJob(WorkflowRevision wf, JobState state) {
		return new BackgroundJob(JobType.MATCHING, null, wf, state, this.clock.instant());
	}

	@Test
	@DisplayName("Stale workflow revision marks matching job as STALE without running solver")
	void staleWorkflowRevisionMarksJobStale() {
		MatchingSnapshotFactory mockSnapshotFactory = mock(MatchingSnapshotFactory.class);
		AppointmentSchedulingSolver mockSolver = mock(AppointmentSchedulingSolver.class);
		OfferService mockOfferService = mock(OfferService.class);
		StaffFallbackPort mockFallbackPort = mock(StaffFallbackPort.class);
		BackgroundJobRepository mockJobRepo = mock(BackgroundJobRepository.class);
		SchedulingRequestRepository mockReqRepo = mock(SchedulingRequestRepository.class);
		WorkflowRevisionRepository mockWfRepo = mock(WorkflowRevisionRepository.class);

		MatchingJobCoordinator coordinator = new MatchingJobCoordinator(mockSnapshotFactory, mockSolver,
				mockOfferService, mockFallbackPort, mockJobRepo, mockReqRepo, mockWfRepo, this.clock);

		SchedulingRequest req = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req.setId(100L);
		WorkflowRevision currentWf = createWorkflowRevision(req, 1, 30, Urgency.ROUTINE, null);
		currentWf.setId(201L);
		req.setCurrentWorkflowRevision(currentWf);

		WorkflowRevision olderWf = createWorkflowRevision(req, 0, 30, Urgency.ROUTINE, null);
		olderWf.setId(200L);

		BackgroundJob job = createMatchingJob(olderWf, JobState.RUNNING);
		job.setId(300L);

		coordinator.executeMatchingJob(job);

		assertThat(job.getState()).isEqualTo(JobState.STALE);
		verify(mockJobRepo).save(job);
		verify(mockSnapshotFactory, times(0)).buildSnapshot(any());
	}

	@Test
	@DisplayName("Empty candidate slots routes request to staff fallback queue as NO_MATCH")
	void emptyCandidatesRoutesToFallbackQueue() {
		MatchingSnapshotFactory mockSnapshotFactory = mock(MatchingSnapshotFactory.class);
		AppointmentSchedulingSolver mockSolver = mock(AppointmentSchedulingSolver.class);
		OfferService mockOfferService = mock(OfferService.class);
		StaffFallbackPort mockFallbackPort = mock(StaffFallbackPort.class);
		BackgroundJobRepository mockJobRepo = mock(BackgroundJobRepository.class);
		SchedulingRequestRepository mockReqRepo = mock(SchedulingRequestRepository.class);
		WorkflowRevisionRepository mockWfRepo = mock(WorkflowRevisionRepository.class);

		MatchingJobCoordinator coordinator = new MatchingJobCoordinator(mockSnapshotFactory, mockSolver,
				mockOfferService, mockFallbackPort, mockJobRepo, mockReqRepo, mockWfRepo, this.clock);

		SchedulingRequest req = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req.setId(100L);
		WorkflowRevision wf = createWorkflowRevision(req, 1, 30, Urgency.ROUTINE, null);
		wf.setId(200L);
		req.setCurrentWorkflowRevision(wf);

		BackgroundJob job = createMatchingJob(wf, JobState.RUNNING);
		job.setId(300L);

		given(mockSnapshotFactory.buildSnapshot(wf))
			.willReturn(new MatchingSnapshotFactory.SnapshotResult(1L, List.of()));

		coordinator.executeMatchingJob(job);

		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.NO_MATCH);
		verify(mockFallbackPort).sendToFallbackQueue(eq(100L), eq("NO_MATCH"), eq(Urgency.ROUTINE), anyString());
	}

	@Test
	@DisplayName("Solver failing to match candidate routes request to staff fallback queue as NO_MATCH")
	void solverFailingToMatchRoutesToFallbackQueue() {
		MatchingSnapshotFactory mockSnapshotFactory = mock(MatchingSnapshotFactory.class);
		AppointmentSchedulingSolver mockSolver = mock(AppointmentSchedulingSolver.class);
		OfferService mockOfferService = mock(OfferService.class);
		StaffFallbackPort mockFallbackPort = mock(StaffFallbackPort.class);
		BackgroundJobRepository mockJobRepo = mock(BackgroundJobRepository.class);
		SchedulingRequestRepository mockReqRepo = mock(SchedulingRequestRepository.class);
		WorkflowRevisionRepository mockWfRepo = mock(WorkflowRevisionRepository.class);

		MatchingJobCoordinator coordinator = new MatchingJobCoordinator(mockSnapshotFactory, mockSolver,
				mockOfferService, mockFallbackPort, mockJobRepo, mockReqRepo, mockWfRepo, this.clock);

		SchedulingRequest req = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req.setId(100L);
		WorkflowRevision wf = createWorkflowRevision(req, 1, 30, Urgency.ROUTINE, null);
		wf.setId(200L);
		req.setCurrentWorkflowRevision(wf);

		BackgroundJob job = createMatchingJob(wf, JobState.RUNNING);
		job.setId(300L);

		CandidateSlot slot = new CandidateSlot(1, "Helen Leary", Instant.parse("2026-09-07T10:00:00Z"),
				Instant.parse("2026-09-07T10:30:00Z"), "America/Chicago", 30, true, true, false, 60, 0,
				"1:2026-09-07T10:00:00Z");

		given(mockSnapshotFactory.buildSnapshot(wf))
			.willReturn(new MatchingSnapshotFactory.SnapshotResult(1L, List.of(slot)));
		given(mockSolver.solve(any())).willReturn(Optional.empty());

		coordinator.executeMatchingJob(job);

		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.NO_MATCH);
		verify(mockFallbackPort).sendToFallbackQueue(eq(100L), eq("NO_MATCH"), eq(Urgency.ROUTINE), anyString());
	}

	@Test
	@DisplayName("Calendar conflict on first attempt retries and succeeds if slot available on second attempt")
	void calendarConflictRetriesAndSucceeds() {
		MatchingSnapshotFactory mockSnapshotFactory = mock(MatchingSnapshotFactory.class);
		AppointmentSchedulingSolver mockSolver = mock(AppointmentSchedulingSolver.class);
		OfferService mockOfferService = mock(OfferService.class);
		StaffFallbackPort mockFallbackPort = mock(StaffFallbackPort.class);
		BackgroundJobRepository mockJobRepo = mock(BackgroundJobRepository.class);
		SchedulingRequestRepository mockReqRepo = mock(SchedulingRequestRepository.class);
		WorkflowRevisionRepository mockWfRepo = mock(WorkflowRevisionRepository.class);

		MatchingJobCoordinator coordinator = new MatchingJobCoordinator(mockSnapshotFactory, mockSolver,
				mockOfferService, mockFallbackPort, mockJobRepo, mockReqRepo, mockWfRepo, this.clock);

		SchedulingRequest req = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req.setId(100L);
		WorkflowRevision wf = createWorkflowRevision(req, 1, 30, Urgency.ROUTINE, null);
		wf.setId(200L);
		req.setCurrentWorkflowRevision(wf);

		BackgroundJob job = createMatchingJob(wf, JobState.RUNNING);
		job.setId(300L);

		CandidateSlot slot1 = new CandidateSlot(1, "Helen Leary", Instant.parse("2026-09-07T10:00:00Z"),
				Instant.parse("2026-09-07T10:30:00Z"), "America/Chicago", 30, true, true, false, 60, 0, "1:slot1");
		CandidateSlot slot2 = new CandidateSlot(1, "Helen Leary", Instant.parse("2026-09-07T10:30:00Z"),
				Instant.parse("2026-09-07T11:00:00Z"), "America/Chicago", 30, true, true, false, 90, 0, "1:slot2");

		given(mockSnapshotFactory.buildSnapshot(wf))
			.willReturn(new MatchingSnapshotFactory.SnapshotResult(1L, List.of(slot1)))
			.willReturn(new MatchingSnapshotFactory.SnapshotResult(2L, List.of(slot2)));

		given(mockSolver.solve(any())).willReturn(Optional.of(slot1)).willReturn(Optional.of(slot2));

		Offer offer = new Offer(req, wf, 1, 1, 1, OfferOrigin.AUTOMATIC, slot2.getStartAt(), slot2.getEndAt(),
				slot2.getZoneId(), Instant.parse("2026-09-07T10:10:00Z"), OfferState.HELD, 1, 2L, "Matched");

		given(mockOfferService.createHeldOffer(eq(req), eq(wf), eq(slot1), eq(OfferOrigin.AUTOMATIC), eq(1L), any()))
			.willThrow(new AvailabilityConflictException("Conflict"));
		given(mockOfferService.createHeldOffer(eq(req), eq(wf), eq(slot2), eq(OfferOrigin.AUTOMATIC), eq(2L), any()))
			.willReturn(offer);

		coordinator.executeMatchingJob(job);

		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.SUCCESS);
		assertThat(job.getCalendarRetryCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("Calendar conflict exceeding max retries sends request to staff fallback queue as CALENDAR_CHANGED")
	void calendarConflictExhaustedSendsToFallbackQueue() {
		MatchingSnapshotFactory mockSnapshotFactory = mock(MatchingSnapshotFactory.class);
		AppointmentSchedulingSolver mockSolver = mock(AppointmentSchedulingSolver.class);
		OfferService mockOfferService = mock(OfferService.class);
		StaffFallbackPort mockFallbackPort = mock(StaffFallbackPort.class);
		BackgroundJobRepository mockJobRepo = mock(BackgroundJobRepository.class);
		SchedulingRequestRepository mockReqRepo = mock(SchedulingRequestRepository.class);
		WorkflowRevisionRepository mockWfRepo = mock(WorkflowRevisionRepository.class);

		MatchingJobCoordinator coordinator = new MatchingJobCoordinator(mockSnapshotFactory, mockSolver,
				mockOfferService, mockFallbackPort, mockJobRepo, mockReqRepo, mockWfRepo, this.clock);

		SchedulingRequest req = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req.setId(100L);
		WorkflowRevision wf = createWorkflowRevision(req, 1, 30, Urgency.ROUTINE, null);
		wf.setId(200L);
		req.setCurrentWorkflowRevision(wf);

		BackgroundJob job = createMatchingJob(wf, JobState.RUNNING);
		job.setId(300L);

		CandidateSlot slot = new CandidateSlot(1, "Helen Leary", Instant.parse("2026-09-07T10:00:00Z"),
				Instant.parse("2026-09-07T10:30:00Z"), "America/Chicago", 30, true, true, false, 60, 0, "1:slot1");

		given(mockSnapshotFactory.buildSnapshot(wf))
			.willReturn(new MatchingSnapshotFactory.SnapshotResult(1L, List.of(slot)));
		given(mockSolver.solve(any())).willReturn(Optional.of(slot));
		given(mockOfferService.createHeldOffer(any(), any(), any(), any(), anyLong(), any()))
			.willThrow(new AvailabilityConflictException("Conflict"));

		coordinator.executeMatchingJob(job);

		assertThat(job.getState()).isEqualTo(JobState.SUCCEEDED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.CALENDAR_CHANGED);
		verify(mockFallbackPort).sendToFallbackQueue(eq(100L), eq("CALENDAR_CHANGED"), eq(Urgency.ROUTINE),
				anyString());
	}

	@Test
	@DisplayName("Concurrent matching jobs competing for same slot: calendar locking ensures exactly one hold created without overlap")
	void concurrentMatchingJobsForConflictingSlots_calendarLockPreventsOverlap() throws Exception {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate targetDate = LocalDate.now().plusDays(2);
		LocalTime startTime = LocalTime.of(9, 0);
		LocalTime endTime = LocalTime.of(9, 30);

		SchedulingRequest req1 = createRequest(1, 1, RequestState.READY_TO_MATCH);
		req1 = this.requestRepository.save(req1);
		WorkflowRevision wf1 = createWorkflowRevision(req1, 1, 30, Urgency.ROUTINE, 1);
		wf1.addAvailabilityWindow(new AvailabilityWindow(wf1, WindowClassification.ALLOWED, WindowShape.ONE_OFF,
				targetDate, null, null, null, startTime, endTime, "Morning", "Confirmed"));
		wf1 = this.workflowRevisionRepository.save(wf1);
		req1.setCurrentWorkflowRevision(wf1);
		this.requestRepository.save(req1);
		this.activeRequestRepository.save(new ActiveSchedulingRequest(1, req1.getId()));

		SchedulingRequest req2 = createRequest(2, 2, RequestState.READY_TO_MATCH);
		req2 = this.requestRepository.save(req2);
		WorkflowRevision wf2 = createWorkflowRevision(req2, 1, 30, Urgency.ROUTINE, 1);
		wf2.addAvailabilityWindow(new AvailabilityWindow(wf2, WindowClassification.ALLOWED, WindowShape.ONE_OFF,
				targetDate, null, null, null, startTime, endTime, "Morning", "Confirmed"));
		wf2 = this.workflowRevisionRepository.save(wf2);
		req2.setCurrentWorkflowRevision(wf2);
		this.requestRepository.save(req2);
		this.activeRequestRepository.save(new ActiveSchedulingRequest(2, req2.getId()));

		BackgroundJob job1 = createMatchingJob(wf1, JobState.PENDING);
		job1 = this.jobRepository.save(job1);

		BackgroundJob job2 = createMatchingJob(wf2, JobState.PENDING);
		job2 = this.jobRepository.save(job2);

		final Long jobId1 = job1.getId();
		final Long jobId2 = job2.getId();

		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<Void> task1 = () -> {
			startLatch.await();
			BackgroundJob j = this.jobRepository.findById(jobId1).orElseThrow();
			this.matchingJobCoordinator.executeMatchingJob(j);
			return null;
		};

		Callable<Void> task2 = () -> {
			startLatch.await();
			BackgroundJob j = this.jobRepository.findById(jobId2).orElseThrow();
			this.matchingJobCoordinator.executeMatchingJob(j);
			return null;
		};

		Future<Void> f1 = this.executorService.submit(task1);
		Future<Void> f2 = this.executorService.submit(task2);

		startLatch.countDown();

		f1.get(30, TimeUnit.SECONDS);
		f2.get(30, TimeUnit.SECONDS);

		List<Offer> offers = this.offerRepository.findAll();
		List<Offer> heldOffers = offers.stream().filter(o -> o.getState() == OfferState.HELD).toList();

		long overlappingHolds = heldOffers.stream()
			.filter(o -> o.getVetId().equals(1)
					&& o.getStartAt().equals(ZonedDateTime.of(targetDate.atTime(startTime), zoneId).toInstant()))
			.count();

		assertThat(overlappingHolds).isLessThanOrEqualTo(1);
	}

}
