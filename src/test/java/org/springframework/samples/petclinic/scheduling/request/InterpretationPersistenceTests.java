package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.DayOfWeek;
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
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationJobRunner;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
class InterpretationPersistenceTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

	private static final LocalTime TIME = LocalTime.of(10, 15, 30);

	@Autowired
	private RequestService requestService;

	@MockitoSpyBean
	private InterpretationJobRunner jobRunner;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private InterpretationRepository interpretations;

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
		doNothing().when(this.jobRunner).launch(anyInt());
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
	@Tag("AC-41")
	void ac41_success_creates_current_ai_version() {
		int requestId = startAndConsentRequest();

		Vet vet = this.transactionTemplate.execute(status -> this.entityManager.find(Vet.class, 3));
		Interpretation interpretation = new Interpretation(true, CareType.SPECIALTY, "surgery", null, 30, vet,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

		SchedulingRequest updated = this.requestService.interpretationSucceeded(requestId, interpretation);
		assertThat(updated.getState()).isEqualTo(RequestState.INTERPRETED);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.INTERPRETED);
			assertThat(reloaded.getInterpretations()).hasSize(1);

			Interpretation current = reloaded.getCurrentInterpretation();
			assertThat(current).isNotNull();
			assertThat(current.getOrigin()).isEqualTo(InterpretationOrigin.AI);
			assertThat(current.isUnderstood()).isTrue();
			assertThat(current.getSpecialty()).isEqualTo("surgery");

			Integer currentIdInDb = this.jdbc.queryForObject(
					"select current_interpretation_id from scheduling_requests where id = ?", Integer.class, requestId);
			assertThat(currentIdInDb).isEqualTo(current.getId());
			return null;
		});
	}

	@Test
	@Tag("AC-42")
	void ac42_all_fields_round_trip_identically() {
		int requestId = startAndConsentRequest();

		Vet vet = this.transactionTemplate.execute(status -> this.entityManager.find(Vet.class, 3));
		String rawJson = "{\"understood\":true,\"careType\":\"SPECIALTY\",\"specialty\":\"OTHER\",\"specialtyLabel\":\"acupuncture\",\"durationMinutes\":90,\"preferredVetId\":3}";

		Interpretation interpretation = new Interpretation(true, CareType.SPECIALTY, "OTHER", "acupuncture", 90, vet,
				InterpretationOrigin.AI, rawJson, "ministral-3:14b", "v1", DATE, TIME);

		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		interpretation.addWindow(
				InterpretationWindow.preferred(LocalDate.of(2026, 9, 15), LocalTime.of(14, 0), LocalTime.of(17, 0)));
		interpretation
			.addWindow(InterpretationWindow.allowed(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(13, 0)));
		interpretation.addWindow(
				InterpretationWindow.allowed(LocalDate.of(2026, 9, 16), LocalTime.of(11, 0), LocalTime.of(15, 0)));
		interpretation
			.addWindow(InterpretationWindow.excluded(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(16, 0)));
		interpretation.addWindow(
				InterpretationWindow.excluded(LocalDate.of(2026, 9, 18), LocalTime.of(9, 0), LocalTime.of(12, 0)));

		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			Interpretation loaded = reloaded.getCurrentInterpretation();
			assertThat(loaded).isNotNull();

			assertThat(loaded.isUnderstood()).isTrue();
			assertThat(loaded.getCareType()).isEqualTo(CareType.SPECIALTY);
			assertThat(loaded.getSpecialty()).isEqualTo("OTHER");
			assertThat(loaded.getSpecialtyLabel()).isEqualTo("acupuncture");
			assertThat(loaded.getDurationMinutes()).isEqualTo(90);
			assertThat(loaded.getPreferredVet()).isNotNull();
			assertThat(loaded.getPreferredVet().getId()).isEqualTo(vet.getId());
			assertThat(loaded.getOrigin()).isEqualTo(InterpretationOrigin.AI);
			assertThat(loaded.getRawJson()).isEqualTo(rawJson);
			assertThat(loaded.getModelTag()).isEqualTo("ministral-3:14b");
			assertThat(loaded.getPromptVersion()).isEqualTo("v1");
			assertThat(loaded.getCreatedDate()).isEqualTo(DATE);
			assertThat(loaded.getCreatedTime()).isEqualTo(TIME);

			List<InterpretationWindow> preferred = loaded.getPreferredWindows();
			assertThat(preferred).hasSize(2);
			assertThat(preferred).anySatisfy(window -> {
				assertThat(window.getWeekday()).isEqualTo(DayOfWeek.MONDAY);
				assertThat(window.getDate()).isNull();
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(9, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(12, 0));
			});
			assertThat(preferred).anySatisfy(window -> {
				assertThat(window.getWeekday()).isNull();
				assertThat(window.getDate()).isEqualTo(LocalDate.of(2026, 9, 15));
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(14, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(17, 0));
			});

			List<InterpretationWindow> allowed = loaded.getAllowedWindows();
			assertThat(allowed).hasSize(2);
			assertThat(allowed).anySatisfy(window -> {
				assertThat(window.getWeekday()).isEqualTo(DayOfWeek.WEDNESDAY);
				assertThat(window.getDate()).isNull();
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(10, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(13, 0));
			});
			assertThat(allowed).anySatisfy(window -> {
				assertThat(window.getWeekday()).isNull();
				assertThat(window.getDate()).isEqualTo(LocalDate.of(2026, 9, 16));
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(11, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(15, 0));
			});

			List<InterpretationWindow> excluded = loaded.getExcludedWindows();
			assertThat(excluded).hasSize(2);
			assertThat(excluded).anySatisfy(window -> {
				assertThat(window.getWeekday()).isEqualTo(DayOfWeek.FRIDAY);
				assertThat(window.getDate()).isNull();
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(13, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(16, 0));
			});
			assertThat(excluded).anySatisfy(window -> {
				assertThat(window.getWeekday()).isNull();
				assertThat(window.getDate()).isEqualTo(LocalDate.of(2026, 9, 18));
				assertThat(window.getStartTime()).isEqualTo(LocalTime.of(9, 0));
				assertThat(window.getEndTime()).isEqualTo(LocalTime.of(12, 0));
			});

			return null;
		});
	}

	@Test
	@Tag("AC-43")
	void ac43_other_null_and_absent_values_are_not_substituted() {
		int requestId = startAndConsentRequest();

		InterpretationResult.ModelOutput output = new InterpretationResult.ModelOutput(true, null, "cardiology",
				"Heart check", null, 999, List.of(new InterpretationResult.Window(DayOfWeek.MONDAY, null,
						LocalTime.of(9, 0), LocalTime.of(12, 0))),
				List.of(), List.of());
		Interpretation interpretation = this.jobRunner.mapToInterpretation(output, "{\"understood\":true}");

		assertThat(interpretation.getSpecialty()).isEqualTo("OTHER");
		assertThat(interpretation.getSpecialtyLabel()).isEqualTo("Heart check");
		assertThat(interpretation.getPreferredVet()).isNull();
		assertThat(interpretation.getCareType()).isNull();
		assertThat(interpretation.getDurationMinutes()).isNull();

		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			Interpretation loaded = reloaded.getCurrentInterpretation();
			assertThat(loaded).isNotNull();

			assertThat(loaded.getSpecialty()).isEqualTo("OTHER");
			assertThat(loaded.getSpecialtyLabel()).isEqualTo("Heart check");
			assertThat(loaded.getPreferredVet()).isNull();
			assertThat(loaded.getCareType()).isNull();
			assertThat(loaded.getDurationMinutes()).isNull();

			Map<String, Object> row = this.jdbc.queryForMap(
					"select care_type, duration_minutes, preferred_vet_id, specialty, specialty_label from interpretations where id = ?",
					loaded.getId());
			assertThat(row.get("care_type")).isNull();
			assertThat(row.get("duration_minutes")).isNull();
			assertThat(row.get("preferred_vet_id")).isNull();
			assertThat(row.get("specialty")).isEqualTo("OTHER");
			assertThat(row.get("specialty_label")).isEqualTo("Heart check");

			return null;
		});
	}

	@Test
	@Tag("AC-44")
	void ac44_defaults_apply_only_during_matching() {
		int requestId = startAndConsentRequest();

		InterpretationResult.ModelOutput output = new InterpretationResult.ModelOutput(true, null, null, null, null,
				null, List.of(new InterpretationResult.Window(DayOfWeek.TUESDAY, null, LocalTime.of(10, 0),
						LocalTime.of(12, 0))),
				List.of(), List.of());
		Interpretation interpretation = this.jobRunner.mapToInterpretation(output, "{\"understood\":true}");

		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			Interpretation loaded = reloaded.getCurrentInterpretation();
			assertThat(loaded).isNotNull();

			assertThat(loaded.getCareType()).isNull();
			assertThat(loaded.getDurationMinutes()).isNull();

			int configuredDefaultDuration = this.jdbc
				.queryForObject("select default_duration_minutes from clinic_settings", Integer.class);
			assertThat(configuredDefaultDuration).isEqualTo(30);

			assertThat(loaded.getEffectiveCareType()).isEqualTo(CareType.GENERAL);
			assertThat(loaded.getEffectiveDurationMinutes(configuredDefaultDuration)).isEqualTo(30);
			assertThat(loaded.getEffectiveDurationMinutes(45)).isEqualTo(45);

			return null;
		});
	}

	@Test
	@Tag("AC-45")
	void ac45_each_failure_kind_retains_raw_and_increments() {
		testFailureKind(InterpretationFailureKind.UNPARSEABLE, "{not-json");
		clearDatabase();

		testFailureKind(InterpretationFailureKind.ZERO_WINDOWS, "{\"understood\":true,\"preferredWindows\":[]}");
		clearDatabase();

		testFailureKind(InterpretationFailureKind.NOT_UNDERSTOOD, "{\"understood\":false}");

		this.transactionTemplate.execute(status -> {
			List<SchedulingRequest> all = this.requests.findAll();
			assertThat(all).hasSize(1);
			SchedulingRequest request = all.get(0);
			assertThat(request.getFailureCount()).isEqualTo(1);
			return null;
		});

		int requestId = this.jdbc.queryForObject("select id from scheduling_requests", Integer.class);
		this.requestService.editText(requestId, "Rephrased inquiry about surgery");
		this.requestService.consent(requestId);
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "second failure payload", DATE, TIME));

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
			assertThat(reloaded.getFailureCount()).isEqualTo(2);
			assertThat(reloaded.getFailures()).hasSize(2);
			return null;
		});
	}

	@Test
	@Tag("AC-48")
	void ac48_late_interpretation_result_is_discarded() {
		int requestId = startAndConsentRequest();
		this.requestService.abandon(requestId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.ABANDONED);
			return null;
		});

		Vet vet = this.transactionTemplate.execute(status -> this.entityManager.find(Vet.class, 1));
		Interpretation late = new Interpretation(true, CareType.GENERAL, null, null, 30, vet, InterpretationOrigin.AI,
				"{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);

		this.requestService.interpretationSucceeded(requestId, late);
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "late raw", DATE, TIME));

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.ABANDONED);
			assertThat(reloaded.getInterpretations()).isEmpty();
			assertThat(reloaded.getCurrentInterpretation()).isNull();
			assertThat(reloaded.getFailures()).isEmpty();
			return null;
		});
	}

	private void testFailureKind(InterpretationFailureKind kind, String rawOutput) {
		int requestId = startAndConsentRequest();

		this.requestService.interpretationFailed(requestId, new InterpretationFailure(kind, rawOutput, DATE, TIME));

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.INTERPRETATION_FAILED);
			assertThat(reloaded.getFailureCount()).isEqualTo(1);
			assertThat(reloaded.getInterpretations()).isEmpty();
			assertThat(reloaded.getCurrentInterpretation()).isNull();

			assertThat(reloaded.getFailures()).hasSize(1);
			InterpretationFailure failure = reloaded.getFailures().get(0);
			assertThat(failure.getFailureKind()).isEqualTo(kind);
			assertThat(failure.getRawOutput()).isEqualTo(rawOutput);
			assertThat(failure.getCreatedDate()).isEqualTo(DATE);
			assertThat(failure.getCreatedTime()).isEqualTo(TIME);
			return null;
		});
	}

	private int startAndConsentRequest() {
		int requestId = this.requestService.createForOwner(1, "Cat needs dental check").getId();
		this.requestService.consent(requestId);
		return requestId;
	}

}
