package org.springframework.samples.petclinic.scheduling.performance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.HoldAcquisitionOutcome;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.appointment.StaleAcquisitionException;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.interpretation.ClinicVocabulary;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationCoordinator;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.HoursFact;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.matching.SlotScorePolicy;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionResult;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.matching.TimeWindow;
import org.springframework.samples.petclinic.scheduling.matching.TimefoldSlotSolver;
import org.springframework.samples.petclinic.scheduling.matching.VetFact;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecord;
import org.springframework.samples.petclinic.scheduling.request.InterpretationRecordRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestTextRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingPocScaleTests {

	private static final String INTERPRETATION = """
			{"schemaVersion":"1.0","visitReason":"Wellness examination","durationMinutes":30,
			"careType":"GENERAL","requiredSpecialtyCode":null,"allowedWindows":[{"sourcePhrase":"Tuesday morning",
			"resolvedStart":"2026-09-01T09:00:00-05:00","resolvedEnd":"2026-09-01T12:00:00-05:00",
			"resolution":"RESOLVED","fallbackAllowed":true}],"preferredWindows":[],"excludedWindows":[],
			"preferredVeterinarianCode":null,"veterinarianPreferenceStrength":"NONE",
			"urgency":"NO_CONCERN_IDENTIFIED","unresolvedDates":[],"uncertainties":[],"confidence":0.91}
			""";

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	InterpretationCoordinator coordinator;

	@Autowired
	ReservationService reservations;

	@Autowired
	ReservationBlockRepository blocks;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	RequestTextRevisionRepository texts;

	@Autowired
	InterpretationRecordRepository interpretations;

	@Autowired
	RequestRevisionRepository revisions;

	@Autowired
	AvailabilityRepository policies;

	@MockitoBean
	InterpretationPort interpretationPort;

	@MockitoBean
	TimefoldSlotSolver solver;

	@Test
	@Timeout(60)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void tenThousandRecordsAndTwentyFiveUsersMeetDeadlinesWithoutLostOrDuplicateReservations() throws Exception {
		given(this.interpretationPort.interpret(any())).willReturn(new InterpretationPort.InterpretationCallResult(
				INTERPRETATION, "gemma4:latest", "gemma4:latest", false));
		given(this.solver.solve(any(), any())).willAnswer(invocation -> selected(invocation.getArgument(0)));
		seedAuditHistory(10_000);
		assertThat(this.jdbc.queryForObject("select count(*) from audit_events", Integer.class))
			.isGreaterThanOrEqualTo(10_000);

		ClinicVocabulary vocabulary = new ClinicVocabulary(Set.of(30), Set.of(), Set.of(), Map.of(), Map.of(),
				"America/Chicago");
		Instant interpretDeadline = Instant.now().plusSeconds(10);
		long interpretStarted = System.nanoTime();
		ExecutorService pool = Executors.newFixedThreadPool(25);
		CountDownLatch ready = new CountDownLatch(25);
		CountDownLatch gate = new CountDownLatch(1);
		List<Future<?>> interpretJobs = new ArrayList<>();
		try {
			for (int i = 0; i < 25; i++) {
				interpretJobs.add(pool.submit(() -> {
					ready.countDown();
					gate.await(5, TimeUnit.SECONDS);
					this.coordinator.interpret(new InterpretationPort.InterpretationCallRequest(
							"Leo needs a wellness exam next week in the morning", Instant.now(), interpretDeadline,
							"prompt", "schema", vocabulary));
					return null;
				}));
			}
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			gate.countDown();
			for (Future<?> job : interpretJobs) {
				job.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(Duration.ofNanos(System.nanoTime() - interpretStarted)).isLessThanOrEqualTo(Duration.ofSeconds(10));

		Instant matchStart = Instant.parse("2026-05-01T09:00:00Z");
		List<SchedulingRequest> matchRequests = new ArrayList<>();
		List<CandidateSlot> matchSlots = new ArrayList<>();
		for (int i = 0; i < 25; i++) {
			Instant start = matchStart.plus(Duration.ofMinutes(i * 30L));
			matchRequests.add(persistMatchingRequest((i % 10) + 1, (i % 13) + 1, start));
			matchSlots.add(slot(1, start));
		}
		AtomicInteger held = new AtomicInteger();
		pool = Executors.newFixedThreadPool(25);
		CountDownLatch matchReady = new CountDownLatch(25);
		CountDownLatch matchGate = new CountDownLatch(1);
		List<Future<?>> matchJobs = new ArrayList<>();
		long matchStarted = System.nanoTime();
		try {
			for (int i = 0; i < 25; i++) {
				int index = i;
				matchJobs.add(pool.submit(() -> {
					matchReady.countDown();
					matchGate.await(5, TimeUnit.SECONDS);
					CandidateSlot slot = matchSlots.get(index);
					HoldAcquisitionOutcome outcome = this.reservations.acquire(matchRequests.get(index).getId(),
							UUID.randomUUID(), slot, snapshot(slot.startAt(), slot), "EARLIEST_AVAILABLE");
					if (outcome == HoldAcquisitionOutcome.HELD) {
						held.incrementAndGet();
					}
					return null;
				}));
			}
			assertThat(matchReady.await(5, TimeUnit.SECONDS)).isTrue();
			matchGate.countDown();
			for (Future<?> job : matchJobs) {
				job.get(5, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(Duration.ofNanos(System.nanoTime() - matchStarted)).isLessThanOrEqualTo(Duration.ofSeconds(5));
		assertThat(held.get()).isEqualTo(25);
		assertThat(this.blocks.findAll()).isNotEmpty();
		assertThat(this.jdbc.queryForObject("select count(*) from reservation_blocks", Integer.class)).isEqualTo(100);

		Instant contested = Instant.parse("2026-05-02T09:00:00Z");
		SchedulingRequest first = persistMatchingRequest(1, 1, contested);
		SchedulingRequest second = persistMatchingRequest(2, 2, contested);
		CandidateSlot contestedSlot = slot(1, contested);
		SlotSelectionSnapshot contestedSnapshot = snapshot(contested, contestedSlot);
		AtomicInteger contestedWins = new AtomicInteger();
		pool = Executors.newFixedThreadPool(2);
		CountDownLatch raceReady = new CountDownLatch(2);
		CountDownLatch raceGate = new CountDownLatch(1);
		try {
			Future<?> one = pool.submit(() -> {
				raceReady.countDown();
				raceGate.await(5, TimeUnit.SECONDS);
				if (tryAcquire(first.getId(), contestedSlot, contestedSnapshot)) {
					contestedWins.incrementAndGet();
				}
				return null;
			});
			Future<?> two = pool.submit(() -> {
				raceReady.countDown();
				raceGate.await(5, TimeUnit.SECONDS);
				if (tryAcquire(second.getId(), contestedSlot, contestedSnapshot)) {
					contestedWins.incrementAndGet();
				}
				return null;
			});
			assertThat(raceReady.await(5, TimeUnit.SECONDS)).isTrue();
			raceGate.countDown();
			one.get(5, TimeUnit.SECONDS);
			two.get(5, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(contestedWins.get()).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from reservation_blocks where resource_type = 'VETERINARIAN' and resource_id = 1 and block_start = ?",
				Integer.class, contested))
			.isEqualTo(1);
	}

	private void seedAuditHistory(int count) {
		List<Object[]> batch = new ArrayList<>(count);
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		for (int i = 0; i < count; i++) {
			batch.add(new Object[] { "SYSTEM", now, "SEED", "Request", String.valueOf(i) });
		}
		this.jdbc.batchUpdate(
				"insert into audit_events (actor_type, occurred_at, action, target_type, target_id) values (?, ?, ?, ?, ?)",
				batch);
	}

	private SchedulingRequest persistMatchingRequest(int ownerId, int petId, Instant marker) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(ownerId);
		request.setPetId(petId);
		request.setState(RequestState.MATCHING);
		request.setOwnerStatusCode("MATCHING");
		request.setCreatedAt(marker);
		request.setUpdatedAt(marker);
		this.requests.saveAndFlush(request);
		RequestTextRevision text = new RequestTextRevision();
		text.setRequestId(request.getId());
		text.setSequence(1);
		text.setSourceText("Need a wellness exam next week");
		text.setSourceHash("scale-" + request.getId());
		text.setSubmittedAt(marker);
		text.setClinicZoneId("America/Chicago");
		text.setEmergencyScreenVersion("none");
		this.texts.saveAndFlush(text);
		InterpretationRecord interpretation = new InterpretationRecord();
		interpretation.setTextRevisionId(text.getId());
		interpretation.setOrigin("LLM");
		interpretation.setOutcome("VALID_REVIEWABLE");
		interpretation.setCreatedAt(marker);
		this.interpretations.saveAndFlush(interpretation);
		RequestRevision revision = new RequestRevision();
		revision.setRequestId(request.getId());
		revision.setSequence(1);
		revision.setInterpretationId(interpretation.getId());
		revision.setStatus("CONFIRMED");
		revision.setVisitReason("Wellness examination");
		revision.setDurationMinutes(30);
		revision.setCareType("GENERAL");
		revision.setUrgency("NO_CONCERN_IDENTIFIED");
		revision.setVeterinarianPreferenceStrength("NONE");
		revision.setClinicPolicyVersion(this.policies.currentPolicy().getConfigurationVersion());
		revision.setClinicZoneId("America/Chicago");
		revision.setCreatedAt(marker);
		this.revisions.saveAndFlush(revision);
		request.setActiveRequestRevisionId(revision.getId());
		this.requests.saveAndFlush(request);
		return request;
	}

	private boolean tryAcquire(Long requestId, CandidateSlot slot, SlotSelectionSnapshot snapshot) {
		try {
			return this.reservations.acquire(requestId, UUID.randomUUID(), slot, snapshot,
					"EARLIEST_AVAILABLE") == HoldAcquisitionOutcome.HELD;
		}
		catch (StaleAcquisitionException ex) {
			return false;
		}
	}

	private CandidateSlot slot(int veterinarianId, Instant start) {
		return new CandidateSlot(veterinarianId + "@" + start, veterinarianId, start, start.plusSeconds(1800),
				"STANDARD", 0);
	}

	private SlotSelectionSnapshot snapshot(Instant start, CandidateSlot slot) {
		Instant now = start.minusSeconds(3600);
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 1L, 0, MatchingMode.PREFERRED_ONLY, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 1, null, slot.veterinarianId(),
				"NONE", List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), List.of(), Set.of(),
				List.of(new VetFact(slot.veterinarianId(), Set.of())), allClinicHours(),
				allVeterinarianHours(slot.veterinarianId()), List.of(), List.of(slot));
	}

	private List<HoursFact> allClinicHours() {
		List<HoursFact> hours = new ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			hours.add(new HoursFact(null, day, LocalTime.of(0, 0), LocalTime.of(23, 59)));
		}
		return hours;
	}

	private List<HoursFact> allVeterinarianHours(int veterinarianId) {
		List<HoursFact> hours = new ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			hours.add(new HoursFact(veterinarianId, day, LocalTime.of(0, 0), LocalTime.of(23, 59)));
		}
		return hours;
	}

	private SlotSelectionResult selected(SlotSelectionSnapshot snapshot) {
		CandidateSlot slot = snapshot.candidates()
			.stream()
			.filter(candidate -> SlotScorePolicy.baseEligible(snapshot, candidate))
			.findFirst()
			.orElseGet(() -> snapshot.candidates().get(0));
		return new SlotSelectionResult("SELECTED", slot, SlotScorePolicy.score(snapshot, slot), true);
	}

}
