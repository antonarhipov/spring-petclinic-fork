package org.springframework.samples.petclinic.scheduling.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterpretationWebTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

	private static final LocalTime TIME = LocalTime.of(10, 15, 30);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RequestService requestService;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

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
	@Tag("AC-49")
	void ac49_interpreting_renders_polling_script_and_refresh() throws Exception {
		int requestId = startAndConsentRequest();
		String content = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(content).contains("/resources/js/request-status.js");
		assertThat(content).contains("data-request-id=\"" + requestId + "\"");
		assertThat(content).contains("<a ");
		assertThat(content).contains("/my/requests/" + requestId);
		assertThat(content).contains("Refresh");
	}

	@Test
	@Tag("AC-49")
	void ac49_polling_endpoint_returns_json_state_only() throws Exception {
		int requestId = startAndConsentRequest();

		this.mvc.perform(get("/my/requests/{id}/status", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"state\":\"INTERPRETING\"}", true));

		this.mvc.perform(get("/my/requests/{id}/status", requestId))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login"));

		this.mvc.perform(get("/my/requests/{id}/status", requestId).with(user("betty").roles("OWNER")))
			.andExpect(status().isNotFound());

		this.mvc.perform(get("/my/requests/{id}/status", requestId).with(user("staff").roles("STAFF")))
			.andExpect(status().isForbidden());
	}

	@Test
	@Tag("AC-50")
	void ac50_review_renders_every_structured_field() throws Exception {
		int requestId = startAndConsentRequest();

		Vet vet = this.transactionTemplate.execute(status -> this.entityManager.find(Vet.class, 3));
		String rawJson = "{\"understood\":true,\"careType\":\"SPECIALTY\",\"specialty\":\"OTHER\",\"specialtyLabel\":\"acupuncture\",\"durationMinutes\":90,\"preferredVetId\":3}";
		Interpretation interpretation = new Interpretation(true, CareType.SPECIALTY, "OTHER", "acupuncture", 90, vet,
				InterpretationOrigin.AI, rawJson, "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		interpretation.addWindow(
				InterpretationWindow.allowed(LocalDate.of(2026, 9, 16), LocalTime.of(11, 0), LocalTime.of(15, 0)));
		interpretation
			.addWindow(InterpretationWindow.excluded(DayOfWeek.FRIDAY, LocalTime.of(13, 0), LocalTime.of(16, 0)));

		this.requestService.interpretationSucceeded(requestId, interpretation);

		String content = this.mvc
			.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")).locale(Locale.ENGLISH))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(content).contains("Specialty care");
		assertThat(content).contains("OTHER");
		assertThat(content).contains("acupuncture");
		assertThat(content)
			.contains("No veterinarian offers this specialty. Confirming hands the request over to clinic staff.");
		assertThat(content).contains("90 min");
		assertThat(content).contains("60 min");
		assertThat(content).contains("Duration was adjusted to clinic limits.");
		assertThat(content).contains(vet.getFirstName() + " " + vet.getLastName());
		assertThat(content).contains("AI interpretation");
		assertThat(content).contains("Monday");
		assertThat(content).contains("09:00");
		assertThat(content).contains("12:00");
		assertThat(content).contains("11:00");
		assertThat(content).contains("15:00");
		assertThat(content).contains("Friday");
		assertThat(content).contains("13:00");
		assertThat(content).contains("16:00");
		assertThat(content).doesNotContain(rawJson);
		assertThat(content).doesNotContain("understood\":true");
	}

	@Test
	void uc3Extension1c_missingReasonUsesGeneralCareAndTheNormativeDurationOnlyAsDefaults() throws Exception {
		int requestId = this.requestService.createForOwner(1, "Tuesday morning").getId();
		this.requestService.consent(requestId);
		Interpretation interpretation = new Interpretation(true, null, null, null, null, null, InterpretationOrigin.AI,
				"{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.interpretationSucceeded(requestId, interpretation);

		String content = this.mvc
			.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")).locale(Locale.ENGLISH))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(content).contains("General care", "30 min", "AI interpretation");
		assertThat(this.jdbc.queryForMap("select care_type, duration_minutes from interpretations where request_id = ?",
				requestId))
			.containsEntry("CARE_TYPE", null)
			.containsEntry("DURATION_MINUTES", null);

		this.requestService.confirmInterpretation(requestId);
		assertThat(this.jdbc.queryForObject(
				"select datediff(minute, start_time, end_time) from appointments where request_id = ? and status = 'HELD'",
				Integer.class, requestId))
			.isEqualTo(30);
	}

	@Test
	@Tag("AC-51")
	void ac51_failures_one_two_do_not_recommend_staff() throws Exception {
		int requestId = startAndConsentRequest();
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad-raw-1", DATE, TIME));

		String attempt1 = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(attempt1).contains("/edit");
		assertThat(attempt1).contains("/staff-assistance");
		assertThat(attempt1).contains("/abandon");
		assertThat(attempt1).doesNotContain("staff-recommendation");
		assertThat(attempt1).doesNotContain("Needs staff");

		this.requestService.editText(requestId, "Second attempt rephrased");
		this.requestService.consent(requestId);
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.NOT_UNDERSTOOD, "bad-raw-2", DATE, TIME));

		String attempt2 = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(attempt2).contains("/edit");
		assertThat(attempt2).contains("/staff-assistance");
		assertThat(attempt2).contains("/abandon");
		assertThat(attempt2).doesNotContain("staff-recommendation");
		assertThat(attempt2).doesNotContain("Needs staff");
	}

	@Test
	@Tag("AC-52")
	void ac52_failure_three_recommends_staff() throws Exception {
		int requestId = startAndConsentRequest();
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad-raw-1", DATE, TIME));

		this.requestService.editText(requestId, "Second attempt rephrased");
		this.requestService.consent(requestId);
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.NOT_UNDERSTOOD, "bad-raw-2", DATE, TIME));

		this.requestService.editText(requestId, "Third attempt rephrased");
		this.requestService.consent(requestId);
		this.requestService.interpretationFailed(requestId,
				new InterpretationFailure(InterpretationFailureKind.ZERO_WINDOWS, "bad-raw-3", DATE, TIME));

		String attempt3 = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(attempt3).contains("staff-recommendation");
		assertThat(attempt3).contains("Needs staff");
		assertThat(attempt3).contains("/edit");
		assertThat(attempt3).contains("/staff-assistance");
		assertThat(attempt3).contains("/abandon");
	}

	@Test
	@Tag("AC-53")
	void ac53_more_than_three_failures_continues_recommending_staff() throws Exception {
		int requestId = startAndConsentRequest();
		for (int i = 1; i <= 4; i++) {
			this.requestService.interpretationFailed(requestId,
					new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad-raw-" + i, DATE, TIME));
			if (i < 4) {
				this.requestService.editText(requestId, "Attempt " + (i + 1) + " rephrased");
				this.requestService.consent(requestId);
			}
		}

		String attempt4 = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(attempt4).contains("staff-recommendation");
		assertThat(attempt4).contains("Needs staff");
		assertThat(attempt4).contains("/edit");
		assertThat(attempt4).contains("/staff-assistance");
		assertThat(attempt4).contains("/abandon");
	}

	@Test
	@Tag("AC-134")
	void ac134_temporal_values_localize() throws Exception {
		int requestId = startAndConsentRequest();
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.interpretationSucceeded(requestId, interpretation);

		String en = this.mvc
			.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")).locale(Locale.ENGLISH))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(en).contains("Monday");

		String de = this.mvc
			.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")).locale(Locale.GERMAN))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(de).contains("Montag");

		String nl = this.mvc
			.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER"))
				.locale(Locale.forLanguageTag("nl")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(nl).contains("maandag");
	}

	private int startAndConsentRequest() {
		int requestId = this.requestService.createForOwner(1, "Cat needs dental check").getId();
		this.jdbc.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", requestId);
		this.entityManager.clear();
		return requestId;
	}

}
