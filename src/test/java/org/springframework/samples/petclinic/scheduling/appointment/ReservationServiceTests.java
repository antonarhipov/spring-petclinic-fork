package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.config.ClockConfiguration;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.HoursFact;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.matching.TimeWindow;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ ReservationService.class, OccupancyQueryService.class, ClockConfiguration.class })
class ReservationServiceTests {

	@Autowired
	ReservationService reservations;

	@Autowired
	SchedulingRequestRepository requests;

	@Autowired
	RequestTextRevisionRepository texts;

	@Autowired
	InterpretationRecordRepository interpretations;

	@Autowired
	RequestRevisionRepository revisions;

	@Autowired
	OfferRepository offers;

	@Autowired
	HoldRepository holds;

	@Autowired
	AvailabilityRepository policies;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void reservationBlockUniquenessRejectsDuplicateKey() {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		this.jdbc.update("insert into reservation_blocks (resource_type, resource_id, block_start) values (?, ?, ?)",
				"VETERINARIAN", 9001, start);
		assertThatThrownBy(() -> this.jdbc.update(
				"insert into reservation_blocks (resource_type, resource_id, block_start) values (?, ?, ?)",
				"VETERINARIAN", 9001, start))
			.isInstanceOf(Exception.class);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from reservation_blocks where resource_type = ? and resource_id = ? and block_start = ?",
				Integer.class, "VETERINARIAN", 9001, start))
			.isEqualTo(1);
	}

	@Test
	void concurrentBlockInsertsHaveSingleWinner() throws Exception {
		Instant start = Instant.parse("2026-03-16T16:00:00Z");
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch startGate = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		try {
			Future<?> first = pool.submit(() -> insertBlock(start, ready, startGate, successes));
			Future<?> second = pool.submit(() -> insertBlock(start, ready, startGate, successes));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			startGate.countDown();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(successes.get()).isEqualTo(1);
		assertThat(this.jdbc.queryForObject(
				"select count(*) from reservation_blocks where resource_type = ? and resource_id = ? and block_start = ?",
				Integer.class, "VETERINARIAN", 42, start))
			.isEqualTo(1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void acquireExactHoldsStaffSlotWithoutPreferredEligibilityCheck() {
		this.jdbc.update("insert into vets (first_name, last_name) values ('Helen', 'Leary')");
		Integer vetId = this.jdbc.queryForObject("select min(id) from vets", Integer.class);
		SchedulingRequest request = persistMatchingRequest();
		this.jdbc.update("update scheduling_requests set state = ? where id = ?", RequestState.STAFF_HANDLING.name(),
				request.getId());
		Instant start = Instant.parse("2026-03-16T18:00:00Z");
		CandidateSlot slot = new CandidateSlot(vetId + "@" + start, vetId, start, start.plusSeconds(1800), "STAFF", 0);
		HoldAcquisitionOutcome outcome = this.reservations.acquireExact(request.getId(), slot, "STAFF", "STAFF_HOLD");
		assertThat(outcome).isEqualTo(HoldAcquisitionOutcome.HELD);
		assertThat(this.offers.findAll()).isNotEmpty();
		assertThat(this.holds.findAll()).isNotEmpty();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void occupiedSlotLeavesLosingAcquireWithoutOfferOrHold() {
		this.jdbc.update("delete from reservation_blocks");
		this.jdbc.update("delete from holds");
		this.jdbc.update("delete from offers");
		this.jdbc.update("insert into vets (first_name, last_name) values ('James', 'Carter')");
		Integer vetId = this.jdbc.queryForObject("select min(id) from vets", Integer.class);
		SchedulingRequest request = persistMatchingRequest();
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		this.jdbc.update("insert into reservation_blocks (resource_type, resource_id, block_start) values (?, ?, ?)",
				"VETERINARIAN", vetId, start);
		CandidateSlot slot = new CandidateSlot(vetId + "@" + start, vetId, start, start.plusSeconds(1800), "STANDARD",
				0);
		HoldAcquisitionOutcome outcome = this.reservations.acquire(request.getId(), UUID.randomUUID(), slot,
				snapshot(start, slot), "EARLIEST_AVAILABLE");
		assertThat(outcome).isEqualTo(HoldAcquisitionOutcome.STALE);
		assertThat(this.offers.findAll()).isEmpty();
		assertThat(this.holds.findAll()).isEmpty();
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.MATCHING);
	}

	private void insertBlock(Instant start, CountDownLatch ready, CountDownLatch startGate, AtomicInteger successes) {
		ready.countDown();
		try {
			startGate.await(5, TimeUnit.SECONDS);
			this.jdbc.update(
					"insert into reservation_blocks (resource_type, resource_id, block_start) values (?, ?, ?)",
					"VETERINARIAN", 42, start);
			successes.incrementAndGet();
		}
		catch (Exception ignored) {
		}
	}

	private SchedulingRequest persistMatchingRequest() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.MATCHING);
		request.setOwnerStatusCode("MATCHING");
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.requests.saveAndFlush(request);
		RequestTextRevision text = new RequestTextRevision();
		text.setRequestId(request.getId());
		text.setSequence(1);
		text.setSourceText("Need a wellness exam next week");
		text.setSourceHash("abc");
		text.setSubmittedAt(Instant.parse("2026-01-01T00:00:00Z"));
		text.setClinicZoneId("America/Chicago");
		text.setEmergencyScreenVersion("none");
		this.texts.saveAndFlush(text);
		InterpretationRecord interpretation = new InterpretationRecord();
		interpretation.setTextRevisionId(text.getId());
		interpretation.setOrigin("LLM");
		interpretation.setOutcome("VALID_REVIEWABLE");
		interpretation.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
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
		revision.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.revisions.saveAndFlush(revision);
		request.setActiveRequestRevisionId(revision.getId());
		this.requests.saveAndFlush(request);
		return request;
	}

	private SlotSelectionSnapshot snapshot(Instant start, CandidateSlot slot) {
		Instant now = Instant.parse("2026-03-16T14:00:00Z");
		return new SlotSelectionSnapshot("1.0", "slot-selection-1", 1L, 1L, 0, MatchingMode.PREFERRED_ONLY, "UTC", now,
				now.plusSeconds(15 * 60), now.plusSeconds(7 * 24 * 3600), 30, 15, 1L, 1, null, 1, "NONE",
				List.of(new TimeWindow(start, start.plusSeconds(3600), false)), List.of(), List.of(), Set.of(),
				List.of(new VetFact(slot.veterinarianId(), Set.of())),
				List.of(new HoursFact(null, DayOfWeek.MONDAY, LocalTime.of(0, 0), LocalTime.of(23, 59))), List.of(),
				List.of(), List.of(slot));
	}

}
