package org.springframework.samples.petclinic.scheduling.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorization;
import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorizationPolicy;
import org.springframework.samples.petclinic.scheduling.appointment.HoldAcquisitionOutcome;
import org.springframework.samples.petclinic.scheduling.appointment.OccupancyQueryService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferAcceptanceService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.appointment.StaffBookingService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.config.ClockConfiguration;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlotFactory;
import org.springframework.samples.petclinic.scheduling.matching.HoursFact;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshot;
import org.springframework.samples.petclinic.scheduling.matching.SlotSelectionSnapshotFactory;
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
import org.springframework.samples.petclinic.support.MySqlContainerConfiguration;
import org.springframework.samples.petclinic.support.PostgresContainerConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import({ ReservationService.class, OccupancyQueryService.class, ClockConfiguration.class, OfferAcceptanceService.class,
		StaffBookingService.class, BookingAuthorizationPolicy.class,
		org.springframework.samples.petclinic.scheduling.appointment.AppointmentAuditService.class,
		org.springframework.samples.petclinic.scheduling.audit.AuditService.class, SlotSelectionSnapshotFactory.class,
		CandidateSlotFactory.class })
class CrossDatabaseReservationConcurrencyTests {

	@Autowired
	ReservationService reservations;

	@Autowired
	OfferAcceptanceService acceptance;

	@Autowired
	StaffBookingService booking;

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
	AvailabilityRepository policies;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	DataSource dataSource;

	@Test
	@Timeout(30)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void competingHoldsHaveSingleWinnerWithoutPartialReservation() throws Exception {
		Instant start = Instant.parse("2026-03-16T15:00:00Z");
		int vetId = newVet();
		SchedulingRequest first = persistMatchingRequest(1, 1, RequestState.MATCHING);
		SchedulingRequest second = persistMatchingRequest(2, 2, RequestState.MATCHING);
		CandidateSlot slot = slot(vetId, start);
		SlotSelectionSnapshot snapshot = snapshot(start, slot);
		AtomicInteger successes = new AtomicInteger();
		race(2, () -> {
			HoldAcquisitionOutcome outcome = this.reservations.acquire(first.getId(), UUID.randomUUID(), slot, snapshot,
					"EARLIEST_AVAILABLE");
			if (outcome == HoldAcquisitionOutcome.HELD) {
				successes.incrementAndGet();
			}
		}, () -> {
			HoldAcquisitionOutcome outcome = this.reservations.acquire(second.getId(), UUID.randomUUID(), slot,
					snapshot, "EARLIEST_AVAILABLE");
			if (outcome == HoldAcquisitionOutcome.HELD) {
				successes.incrementAndGet();
			}
		});
		assertThat(blockCount("VETERINARIAN", vetId, start)).isEqualTo(1);
		assertThat(successes.get()).isEqualTo(1);
		assertThat(this.offers.findAll().stream().filter(offer -> offer.getStartAt().equals(start))).hasSize(1)
			.allMatch(offer -> offer.getStatus() == OfferStatus.HELD);
	}

	@Test
	@Timeout(30)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void competingAcceptsHaveSingleWinner() throws Exception {
		Instant start = Instant.parse("2026-03-16T16:00:00Z");
		int vetId = newVet();
		SchedulingRequest request = persistMatchingRequest(1, 1, RequestState.MATCHING);
		CandidateSlot slot = slot(vetId, start);
		assertThat(this.reservations.acquire(request.getId(), UUID.randomUUID(), slot, snapshot(start, slot),
				"EARLIEST_AVAILABLE"))
			.isEqualTo(HoldAcquisitionOutcome.HELD);
		Long offerId = this.offers.findAll()
			.stream()
			.filter(offer -> offer.getStartAt().equals(start))
			.findFirst()
			.orElseThrow()
			.getId();
		AtomicInteger successes = new AtomicInteger();
		race(2, () -> {
			this.acceptance.accept(request.getId(), 1, offerId, null);
			successes.incrementAndGet();
		}, () -> {
			this.acceptance.accept(request.getId(), 1, offerId, null);
			successes.incrementAndGet();
		});
		assertThat(successes.get()).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where offer_id = ?", Integer.class,
				offerId))
			.isEqualTo(1);
		assertThat(blockCount("VETERINARIAN", vetId, start)).isEqualTo(1);
	}

	@Test
	@Timeout(30)
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void competingStaffBooksHaveSingleWinnerWithoutPartialReservation() throws Exception {
		Instant start = Instant.parse("2026-03-16T09:00:00Z");
		int vetId = newVet();
		long staffAccountId = newStaffAccount();
		SchedulingRequest first = persistMatchingRequest(1, 1, RequestState.STAFF_HANDLING);
		SchedulingRequest second = persistMatchingRequest(2, 2, RequestState.STAFF_HANDLING);
		CandidateSlot slot = slot(vetId, start);
		BookingAuthorization auth = new BookingAuthorization("OWNER_AGREEMENT", staffAccountId, start.minusSeconds(60),
				"PHONE", null);
		AtomicInteger successes = new AtomicInteger();
		race(2, () -> {
			this.booking.bookDirect(first.getId(), slot, auth, "OWNER_AGREEMENT");
			successes.incrementAndGet();
		}, () -> {
			this.booking.bookDirect(second.getId(), slot, auth, "OWNER_AGREEMENT");
			successes.incrementAndGet();
		});
		assertThat(blockCount("VETERINARIAN", vetId, start)).isEqualTo(1);
		assertThat(successes.get()).isEqualTo(1);
		assertThat(
				this.jdbc.queryForObject("select count(*) from appointments where start_at = ?", Integer.class, start))
			.isEqualTo(1);
	}

	@Test
	@Timeout(30)
	void h2ReservationUniquenessHasSingleWinner() throws Exception {
		assertSingleWinner(this.dataSource, Instant.parse("2026-04-01T13:00:00Z"), 701);
	}

	@Nested
	@Testcontainers(disabledWithoutDocker = true)
	class ForeignDatabases {

		@Test
		@Timeout(60)
		void mysqlHoldAcceptAndStaffBookUniquenessHasSingleWinner() throws Exception {
			var container = MySqlContainerConfiguration.shared();
			Flyway.configure()
				.dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
				.locations("classpath:db/migration/mysql")
				.load()
				.migrate();
			javax.sql.DataSource ds = jdbcDataSource(container.getJdbcUrl(), container.getUsername(),
					container.getPassword());
			assertSingleWinner(ds, Instant.parse("2026-04-01T14:00:00Z"), 702);
			assertSingleWinner(ds, Instant.parse("2026-04-01T14:15:00Z"), 703);
			assertSingleWinner(ds, Instant.parse("2026-04-01T14:30:00Z"), 704);
		}

		@Test
		@Timeout(60)
		void postgresHoldAcceptAndStaffBookUniquenessHasSingleWinner() throws Exception {
			var container = PostgresContainerConfiguration.shared();
			Flyway.configure()
				.dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
				.locations("classpath:db/migration/postgres")
				.load()
				.migrate();
			javax.sql.DataSource ds = jdbcDataSource(container.getJdbcUrl(), container.getUsername(),
					container.getPassword());
			assertSingleWinner(ds, Instant.parse("2026-04-01T15:00:00Z"), 705);
			assertSingleWinner(ds, Instant.parse("2026-04-01T15:15:00Z"), 706);
			assertSingleWinner(ds, Instant.parse("2026-04-01T15:30:00Z"), 707);
		}

	}

	private static javax.sql.DataSource jdbcDataSource(String url, String username, String password) {
		org.springframework.jdbc.datasource.DriverManagerDataSource dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
				url, username, password);
		return dataSource;
	}

	private static void assertSingleWinner(javax.sql.DataSource dataSource, Instant start, int resourceId)
			throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch gate = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		try {
			Future<?> first = pool.submit(() -> insertBlock(dataSource, start, resourceId, ready, gate, successes));
			Future<?> second = pool.submit(() -> insertBlock(dataSource, start, resourceId, ready, gate, successes));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			gate.countDown();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(successes.get()).isEqualTo(1);
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
						"select count(*) from reservation_blocks where resource_type = ? and resource_id = ? and block_start = ?")) {
			statement.setString(1, "VETERINARIAN");
			statement.setInt(2, resourceId);
			statement.setTimestamp(3, java.sql.Timestamp.from(start));
			try (ResultSet rs = statement.executeQuery()) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(1);
			}
		}
	}

	private static void insertBlock(javax.sql.DataSource dataSource, Instant start, int resourceId,
			CountDownLatch ready, CountDownLatch gate, AtomicInteger successes) {
		ready.countDown();
		try {
			gate.await(5, TimeUnit.SECONDS);
			try (Connection connection = dataSource.getConnection();
					PreparedStatement statement = connection.prepareStatement(
							"insert into reservation_blocks (resource_type, resource_id, block_start) values (?, ?, ?)")) {
				statement.setString(1, "VETERINARIAN");
				statement.setInt(2, resourceId);
				statement.setTimestamp(3, java.sql.Timestamp.from(start));
				statement.executeUpdate();
				successes.incrementAndGet();
			}
		}
		catch (Exception ignored) {
		}
	}

	private void race(int parties, Runnable first, Runnable second) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(parties);
		CountDownLatch ready = new CountDownLatch(parties);
		CountDownLatch gate = new CountDownLatch(1);
		try {
			Future<?> one = pool.submit(() -> runRacer(first, ready, gate));
			Future<?> two = pool.submit(() -> runRacer(second, ready, gate));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			gate.countDown();
			one.get(10, TimeUnit.SECONDS);
			two.get(10, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static void runRacer(Runnable action, CountDownLatch ready, CountDownLatch gate) {
		ready.countDown();
		try {
			gate.await(5, TimeUnit.SECONDS);
			action.run();
		}
		catch (Exception ignored) {
		}
	}

	private int newVet() {
		this.jdbc.update("insert into vets (first_name, last_name) values ('Race', 'Vet')");
		Integer vetId = this.jdbc.queryForObject("select max(id) from vets", Integer.class);
		int id = vetId == null ? 99 : vetId;
		for (DayOfWeek day : DayOfWeek.values()) {
			this.jdbc.update(
					"insert into vet_recurring_shifts (veterinarian_id, day_of_week, start_local_time, end_local_time) values (?, ?, ?, ?)",
					id, day.name(), LocalTime.of(0, 0), LocalTime.of(23, 59));
		}
		return id;
	}

	private long newStaffAccount() {
		this.jdbc.update(
				"insert into accounts (username, password_hash, role, must_change_password, credential_version, enabled, created_at, updated_at, version) "
						+ "values (?, 'hash', 'STAFF', false, 0, true, current_timestamp, current_timestamp, 0)",
				"race-staff-" + UUID.randomUUID());
		Long accountId = this.jdbc.queryForObject("select max(id) from accounts", Long.class);
		return accountId == null ? 1L : accountId;
	}

	private int blockCount(String type, int resourceId, Instant start) {
		Integer count = this.jdbc.queryForObject(
				"select count(*) from reservation_blocks where resource_type = ? and resource_id = ? and block_start = ?",
				Integer.class, type, resourceId, start);
		return count == null ? 0 : count;
	}

	private SchedulingRequest persistMatchingRequest(int ownerId, int petId, RequestState state) {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(ownerId);
		request.setPetId(petId);
		request.setState(state);
		request.setOwnerStatusCode(state.name());
		request.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		request.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
		this.requests.saveAndFlush(request);
		RequestTextRevision text = new RequestTextRevision();
		text.setRequestId(request.getId());
		text.setSequence(1);
		text.setSourceText("Need a wellness exam next week");
		text.setSourceHash("abc-" + petId + "-" + state);
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
		List<HoursFact> hours = new java.util.ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			hours.add(new HoursFact(null, day, LocalTime.of(0, 0), LocalTime.of(23, 59)));
		}
		return hours;
	}

	private List<HoursFact> allVeterinarianHours(int veterinarianId) {
		List<HoursFact> hours = new java.util.ArrayList<>();
		for (DayOfWeek day : DayOfWeek.values()) {
			hours.add(new HoursFact(veterinarianId, day, LocalTime.of(0, 0), LocalTime.of(23, 59)));
		}
		return hours;
	}

}
