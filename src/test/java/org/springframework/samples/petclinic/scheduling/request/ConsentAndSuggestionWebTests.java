package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsentAndSuggestionWebTests {

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
	@Tag("AC-32")
	void ac32_consent_disclosure_is_exact() throws Exception {
		int requestId = this.requestService.createForOwner(1, "Dental checkup").getId();

		String html = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("Dental checkup");
		assertThat(html).contains("specialties");
		assertThat(html).contains("veterinarian");
		assertThat(html).contains("opening");
		assertThat(html).contains("part of day");
		assertThat(html).contains("Europe/Amsterdam");
		assertThat(html).contains("2026-09-07");
		assertThat(html).contains("min:15,default:30,max:60");
		assertThat(html).contains("radiology,surgery,dentistry");
		assertThat(html).contains("1:James Carter[]", "3:Linda Douglas[surgery,dentistry]");
		assertThat(html).contains("MONDAY:09:00-17:00", "SATURDAY:closed");
		assertThat(html).contains("No owner or pet identifiers are sent.");

		assertThat(html).doesNotContain("George Franklin");
		assertThat(html).doesNotContain("Leo");

		assertThat(html).contains("/my/requests/" + requestId + "/consent");
		assertThat(html).contains("/my/requests/" + requestId + "/decline");

		assertThat(html).doesNotContain("/my/requests/" + requestId + "/confirm");
		assertThat(html).doesNotContain("/my/requests/" + requestId + "/accept");
		assertThat(html).doesNotContain("/my/requests/" + requestId + "/another-option");
		assertThat(html).doesNotContain("/my/requests/" + requestId + "/staff-assistance");
		assertThat(html).doesNotContain("/my/requests/" + requestId + "/abandon");
	}

	@Test
	@Tag("AC-38")
	void ac38_guidance_and_contact_present() throws Exception {
		int id = this.requestService.createForOwner(1, "Multi-state guidance check").getId();

		// 1. AWAITING_CONSENT
		assertGuidanceAndContact(id);

		// 2. INTERPRETING
		this.requestService.consent(id);
		assertGuidanceAndContact(id);

		// 3. INTERPRETATION_FAILED
		this.requestService.interpretationFailed(id,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad", DATE, TIME));
		assertGuidanceAndContact(id);

		// 4. INTERPRETED
		clearDatabase();
		id = this.requestService.createForOwner(1, "Interpreted guidance check").getId();
		this.requestService.consent(id);
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.interpretationSucceeded(id, interpretation);
		assertGuidanceAndContact(id);

		// 5. SUGGESTION_OFFERED
		this.requestService.confirmInterpretation(id);
		assertGuidanceAndContact(id);

		// 6. WITH_STAFF
		this.requestService.routeToStaff(id);
		assertGuidanceAndContact(id);

		// 7. Terminal request detail
		this.requestService.abandon(id);
		assertGuidanceAndContact(id);

		for (String page : List.of("/my/requests/new", "/my/appointments")) {
			String html = this.mvc.perform(get(page).with(user("george").roles("OWNER")))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html).contains("For urgent care, contact the clinic directly.", "Clinic contact");
		}
	}

	@Test
	void owner_suggestion_has_one_slot_and_irreversibility() throws Exception {
		int requestId = this.requestService.createForOwner(1, "Cat examination").getId();
		this.requestService.consent(requestId);

		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.interpretationSucceeded(requestId, interpretation);
		this.requestService.confirmInterpretation(requestId);

		String html = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("Accepting confirms this appointment and ends the request.");
		assertThat(html).contains("suggestion-vet");
		assertThat(html).contains("suggestion-date");
		assertThat(html).contains("suggestion-time");
		assertThat(html).contains("suggestion-duration");
		assertThat(html).contains("suggestion-rank-reason");
		assertThat(html).contains("suggestion-specialty");
		assertThat(html).containsOnlyOnce("suggestion-vet").containsOnlyOnce("suggestion-date");
		assertThat(html).doesNotContain("/staff/calendar", ">Calendar<");
	}

	private void assertGuidanceAndContact(int requestId) throws Exception {
		String html = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("For urgent care, contact the clinic directly.");
		assertThat(html).contains("Clinic contact");
	}

}
