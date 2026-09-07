package org.springframework.samples.petclinic.scheduling.request;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnerRequestOutcomeTests {

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
	@Tag("AC-53")
	void ac53_recommendation_remains_after_third_failure() throws Exception {
		int requestId = startAndConsentRequest();
		for (int i = 1; i <= 5; i++) {
			this.requestService.interpretationFailed(requestId,
					new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "raw-" + i, DATE, TIME));

			if (i == 4 || i == 5) {
				String body = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString();

				assertThat(body).contains("staff-recommendation");
				assertThat(body).contains("Needs staff");
				assertThat(body).contains("/edit");
				assertThat(body).contains("/staff-assistance");
				assertThat(body).contains("/abandon");
			}

			if (i < 5) {
				this.requestService.editText(requestId, "Attempt " + (i + 1));
				this.requestService.consent(requestId);
			}
		}
	}

	@Test
	@Tag("AC-54")
	void ac54_other_confirmation_routes_without_hold() throws Exception {
		int requestId = startAndConsentRequest();
		Interpretation otherInterpretation = new Interpretation(true, CareType.SPECIALTY, "OTHER", "acupuncture", 30,
				null, InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(requestId, otherInterpretation);

		int appointmentCountBefore = this.jdbc.queryForObject("select count(*) from appointments", Integer.class);

		this.requestService.confirmInterpretation(requestId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(reloaded.getWithStaffReason()).isEqualTo(WithStaffReason.UNMATCHED_SPECIALTY);

			int appointmentCountAfter = this.jdbc.queryForObject("select count(*) from appointments", Integer.class);
			assertThat(appointmentCountAfter).isEqualTo(appointmentCountBefore);
			return null;
		});

		String html = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("With clinic staff");
		assertThat(html).contains("Other specialty note");
		assertThat(html).contains("No veterinarian offers the requested specialty");
		assertThat(html).contains("/abandon");
	}

	@Test
	@Tag("AC-75")
	void no_candidate_supports_owner_flow() throws Exception {
		int requestId = startAndConsentRequest();
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.requestService.confirmInterpretation(requestId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(reloaded.getWithStaffReason()).isEqualTo(WithStaffReason.NO_SLOTS);

			int heldAppointments = this.jdbc.queryForObject(
					"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
					requestId);
			assertThat(heldAppointments).isZero();
			return null;
		});

		String html = this.mvc.perform(get("/my/requests/{id}", requestId).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(html).contains("With clinic staff");
		assertThat(html).contains("No matching slots are available");
		assertThat(html).contains("/abandon");
		assertThat(html).doesNotContain("/edit", "/staff-assistance", "/confirm", "/another-option", "/accept",
				"/consent", "/decline");
	}

	@Test
	@Tag("AC-77")
	void ac77_all_wrong_state_owner_actions_are_noops() throws Exception {
		int id = startAndConsentRequest();

		Map<String, Object> before = this.jdbc.queryForMap("select * from scheduling_requests where id = ?", id);
		List<String> disallowedActions = List.of("confirm", "accept", "another-option");

		for (String action : disallowedActions) {
			this.mvc
				.perform(
						post("/my/requests/{id}/{action}", id, action).with(user("george").roles("OWNER")).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("not available")));

			Map<String, Object> after = this.jdbc.queryForMap("select * from scheduling_requests where id = ?", id);
			assertThat(after).isEqualTo(before);
		}
	}

	@Test
	@Tag("AC-78")
	void ac78_owner_choice_releases_hold_and_records_reason() throws Exception {
		int failedReqId = startAndConsentRequest();
		this.requestService.interpretationFailed(failedReqId,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad", DATE, TIME));
		this.requestService.routeToStaff(failedReqId);
		SchedulingRequest failedReloaded = this.requests.findById(failedReqId).orElseThrow();
		assertThat(failedReloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(failedReloaded.getWithStaffReason()).isEqualTo(WithStaffReason.OWNER_CHOICE);

		clearDatabase();

		int interpretedReqId = startAndConsentRequest();
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(interpretedReqId, interpretation);
		this.requestService.routeToStaff(interpretedReqId);
		SchedulingRequest interpretedReloaded = this.requests.findById(interpretedReqId).orElseThrow();
		assertThat(interpretedReloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(interpretedReloaded.getWithStaffReason()).isEqualTo(WithStaffReason.OWNER_CHOICE);

		clearDatabase();

		int offeredReqId = startAndConsentRequest();
		Interpretation freshInterpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(offeredReqId, freshInterpretation);
		this.transactionTemplate.execute(status -> {
			SchedulingRequest req = this.requests.findById(offeredReqId).orElseThrow();
			req.transitionTo(RequestState.SUGGESTION_OFFERED, DATE, TIME);
			this.requests.save(req);

			Vet vet = this.entityManager.find(Vet.class, 1);
			Appointment held = Appointment.held(req, vet, DATE, TIME, TIME.plusMinutes(30), "check", DATE, TIME);
			this.appointments.save(held);
			return null;
		});

		int heldCountBefore = this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
				offeredReqId);
		assertThat(heldCountBefore).isEqualTo(1);

		this.requestService.routeToStaff(offeredReqId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest offeredReloaded = this.requests.findById(offeredReqId).orElseThrow();
			assertThat(offeredReloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(offeredReloaded.getWithStaffReason()).isEqualTo(WithStaffReason.OWNER_CHOICE);

			int heldCountAfter = this.jdbc.queryForObject(
					"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
					offeredReqId);
			assertThat(heldCountAfter).isZero();
			return null;
		});
	}

	private int startAndConsentRequest() {
		int requestId = this.requestService.createForOwner(1, "Cat needs dental check").getId();
		this.jdbc.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", requestId);
		this.entityManager.clear();
		return requestId;
	}

}
