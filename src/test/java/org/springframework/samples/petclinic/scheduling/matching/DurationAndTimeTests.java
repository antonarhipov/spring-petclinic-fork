package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationLauncher;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DurationAndTimeTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

	private static final LocalTime TIME = LocalTime.of(10, 15, 30);

	@Autowired
	private RequestService requestService;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		this.transactionTemplate = new TransactionTemplate(this.transactionManager);
		clearDatabase();
	}

	private void clearDatabase() {
		this.transactionTemplate.execute(status -> {
			this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
			this.jdbc.update("delete from visits where appointment_id is not null");
			this.jdbc.update("delete from interpretation_windows");
			this.jdbc.update("delete from interpretation_failures");
			this.jdbc.update("delete from request_rejections");
			this.jdbc.update("delete from appointments");
			this.jdbc.update("delete from interpretations");
			this.jdbc.update("delete from scheduling_requests");
			return null;
		});
	}

	@Test
	@Tag("AC-71")
	void ac71_exact_duration_bounds_unchanged() {
		DurationPolicy.DurationResult minResult = DurationPolicy.resolve(15, 15, 30, 60);
		assertThat(minResult.effectiveDuration()).isEqualTo(15);
		assertThat(minResult.clamped()).isFalse();
		assertThat(minResult.clampNoteKey()).isNull();

		DurationPolicy.DurationResult maxResult = DurationPolicy.resolve(60, 15, 30, 60);
		assertThat(maxResult.effectiveDuration()).isEqualTo(60);
		assertThat(maxResult.clamped()).isFalse();
		assertThat(maxResult.clampNoteKey()).isNull();
	}

	@Test
	@Tag("AC-72")
	void ac72_below_and_above_are_clamped_with_key() {
		DurationPolicy.DurationResult below = DurationPolicy.resolve(14, 15, 30, 60);
		assertThat(below.effectiveDuration()).isEqualTo(15);
		assertThat(below.clamped()).isTrue();
		assertThat(below.clampNoteKey()).isEqualTo("scheduling.interpretation.duration.clamped");

		DurationPolicy.DurationResult farBelow = DurationPolicy.resolve(0, 15, 30, 60);
		assertThat(farBelow.effectiveDuration()).isEqualTo(15);
		assertThat(farBelow.clamped()).isTrue();
		assertThat(farBelow.clampNoteKey()).isEqualTo("scheduling.interpretation.duration.clamped");

		DurationPolicy.DurationResult above = DurationPolicy.resolve(61, 15, 30, 60);
		assertThat(above.effectiveDuration()).isEqualTo(60);
		assertThat(above.clamped()).isTrue();
		assertThat(above.clampNoteKey()).isEqualTo("scheduling.interpretation.duration.clamped");

		DurationPolicy.DurationResult farAbove = DurationPolicy.resolve(120, 15, 30, 60);
		assertThat(farAbove.effectiveDuration()).isEqualTo(60);
		assertThat(farAbove.clamped()).isTrue();
		assertThat(farAbove.clampNoteKey()).isEqualTo("scheduling.interpretation.duration.clamped");
	}

	@Test
	@Tag("AC-73")
	void ac73_absent_duration_uses_default() {
		DurationPolicy.DurationResult absent = DurationPolicy.resolve(null, 15, 30, 60);
		assertThat(absent.effectiveDuration()).isEqualTo(30);
		assertThat(absent.clamped()).isFalse();
		assertThat(absent.clampNoteKey()).isNull();
	}

	@Test
	@Tag("AC-80")
	void ac80_time_alone_never_releases_hold() {
		int requestId = this.requestService.createForOwner(1, "Need surgery check").getId();
		this.requestService.consent(requestId);
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(requestId, interpretation);
		this.jdbc.update("update scheduling_requests set state = 'SUGGESTION_OFFERED' where id = ?", requestId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest req = this.requests.findById(requestId).orElseThrow();
			Vet vet = this.entityManager.find(Vet.class, 1);
			Appointment held = Appointment.held(req, vet, DATE, TIME, TIME.plusMinutes(30), "rank", DATE, TIME);
			this.appointments.save(held);
			return null;
		});

		Clock advancedClock = Clock.offset(this.clock, java.time.Duration.ofDays(30));
		assertThat(advancedClock.instant()).isAfter(this.clock.instant());

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);

			List<Appointment> heldList = this.appointments.findAll()
				.stream()
				.filter(a -> a.getRequest() != null && a.getRequest().getId() == requestId)
				.toList();
			assertThat(heldList).hasSize(1);
			assertThat(heldList.get(0).getStatus()).isEqualTo(AppointmentStatus.HELD);
			return null;
		});
	}

	@Test
	@Tag("AC-117")
	void ac117_nine_oclock_survives_dst_boundary() {
		LocalDate dstChangeDate = LocalDate.of(2026, 10, 25);
		LocalDate dayAfterDst = dstChangeDate.plusDays(1);
		LocalTime nineOclock = LocalTime.of(9, 0);

		int requestId = this.requestService.createForOwner(1, "Cat checkup post-DST").getId();
		int appointmentId = this.transactionTemplate.execute(status -> {
			SchedulingRequest req = this.requests.findById(requestId).orElseThrow();
			Pet pet = req.getPet();
			Vet vet = this.entityManager.find(Vet.class, 1);
			Appointment app = Appointment.confirmed(pet, vet, dayAfterDst, nineOclock, nineOclock.plusMinutes(30),
					"DST test", "staff", dayAfterDst, nineOclock);
			return this.appointments.save(app).getId();
		});

		Map<String, Object> row = this.jdbc
			.queryForMap("select appointment_date, start_time, end_time from appointments where id = ?", appointmentId);
		assertThat(row.get("appointment_date")).isEqualTo(java.sql.Date.valueOf(dayAfterDst));
		assertThat(row.get("start_time").toString()).startsWith("09:00");

		this.transactionTemplate.execute(status -> {
			Appointment reloaded = this.appointments.findById(appointmentId).orElseThrow();
			assertThat(reloaded.getDate()).isEqualTo(dayAfterDst);
			assertThat(reloaded.getStartTime()).isEqualTo(nineOclock);
			assertThat(reloaded.getStartTime().getHour()).isEqualTo(9);
			assertThat(reloaded.getStartTime().getMinute()).isZero();
			return null;
		});
	}

}
