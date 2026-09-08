package org.springframework.samples.petclinic.scheduling;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.support.DeterministicInterpreter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Outside-in executable specification for UC-3 and its UC-1 dependency. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SchedulingE2eTests {

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	private static final Pattern REQUEST_LOCATION = Pattern.compile(".*/my/requests/(\\d+)(?:[/?].*)?");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DeterministicInterpreter interpreter;

	private Browser browser;

	@BeforeEach
	void openBrowser() {
		this.browser = new Browser(this.port);
	}

	@AfterEach
	void releaseInterpreter() {
		this.interpreter.releaseBlockingCalls();
	}

	@Test
	@Tag("AC-33")
	@Tag("AC-49")
	@Tag("AC-55")
	@Tag("AC-76")
	void ownerObtainsAppointmentThroughSuggestion() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");

		HttpResponse<String> startPage = this.browser.get("/my/requests/new", 200);
		HttpResponse<String> started = this.browser.post("/my/requests/new", startPage,
				Map.of("petId", "1", "requestText", "Surgery follow-up, mornings this week"), 302);
		int requestId = requestId(started);
		assertRequest(requestId, "AWAITING_CONSENT", 1, "Surgery follow-up, mornings this week");

		HttpResponse<String> consentPage = this.browser.follow(started, 200);
		assertThat(consentPage.body())
			.contains("specialties", "veterinarian", "opening", "part of day", "Europe/Amsterdam", "2026-09-07")
			.doesNotContain("George Franklin");
		this.interpreter.prepareBlocking(1);
		HttpResponse<String> consented = this.browser.post("/my/requests/" + requestId + "/consent", consentPage,
				Map.of(), 302);
		assertRequestState(requestId, "INTERPRETING");
		assertThat(this.interpreter.awaitBlockingCalls(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

		HttpResponse<String> interpreting = this.browser.follow(consented, 200);
		assertThat(interpreting.body()).containsIgnoringCase("refresh");
		HttpResponse<String> status = this.browser.get("/my/requests/" + requestId + "/status", 200);
		assertThat(status.headers().firstValue("Content-Type").orElse("")).contains("application/json");
		assertThat(status.body()).matches("\\s*\\{\\s*\"state\"\\s*:\\s*\"INTERPRETING\"\\s*}\\s*");

		this.interpreter.releaseBlockingCalls();
		awaitState(requestId, "INTERPRETED");
		HttpResponse<String> review = this.browser.get("/my/requests/" + requestId, 200);
		assertThat(review.body()).contains("surgery", "30");
		HttpResponse<String> confirmed = this.browser.post("/my/requests/" + requestId + "/confirm", review, Map.of(),
				302);
		assertRequestState(requestId, "SUGGESTION_OFFERED");

		Map<String, Object> held = onlyAppointment(requestId, "HELD");
		HttpResponse<String> suggestion = this.browser.follow(confirmed, 200);
		assertRenderedSlot(suggestion.body(), held);

		HttpResponse<String> accepted = this.browser.post("/my/requests/" + requestId + "/accept", suggestion, Map.of(),
				302);
		assertRequestState(requestId, "ACCEPTED");
		assertThat(value("select active_pet_id from scheduling_requests where id = ?", requestId)).isNull();
		assertThat(count("select count(*) from appointments where request_id = ? and status = 'HELD'", requestId))
			.isZero();
		assertThat(count("select count(*) from appointments where request_id = ? and status = 'CONFIRMED'", requestId))
			.isOne();
		assertThat(this.browser.get("/my/appointments", 200).body()).contains(text(held.get("APPOINTMENT_DATE")),
				shortTime(held.get("START_TIME")));
		this.browser.follow(accepted, 200);
	}

	@Test
	void uc3Extensions1a1b1dDuplicateOwnershipAndLanguageHandling() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int firstId = startRequest(1, "First request");
		HttpResponse<String> form = this.browser.get("/my/requests/new", 200);
		HttpResponse<String> duplicate = this.browser.post("/my/requests/new", form,
				Map.of("petId", "1", "requestText", "Second request"), 302);
		assertThat(requestId(duplicate)).isEqualTo(firstId);
		assertThat(count("select count(*) from scheduling_requests where active_pet_id = 1")).isOne();
		abandon(firstId);

		List<Map<String, Object>> beforeForeignAttempts = this.jdbc
			.queryForList("select * from scheduling_requests order by id");
		HttpResponse<String> freshForm = this.browser.get("/my/requests/new", 200);
		HttpResponse<String> foreignPet = this.browser.post("/my/requests/new", freshForm,
				Map.of("petId", "3", "requestText", "Must not be created"), 404);
		HttpResponse<String> unknownPet = this.browser.post("/my/requests/new", freshForm,
				Map.of("petId", "999999", "requestText", "Must not be created"), 404);
		assertThat(foreignPet.body()).isEqualTo(unknownPet.body())
			.doesNotContain("Rosy", "Betty", "Must not be created");
		assertThat(this.jdbc.queryForList("select * from scheduling_requests order by id"))
			.isEqualTo(beforeForeignAttempts);

		String nonEnglishText = "Нужна запись во вторник утром";
		int nonEnglishId = startRequest(1, nonEnglishText);
		assertRequest(nonEnglishId, "AWAITING_CONSENT", 1, nonEnglishText);
		advanceToReview(nonEnglishId);
		assertRequestState(nonEnglishId, "INTERPRETED");
		assertThat(this.interpreter.getPrompts()).singleElement().asString().contains("requestText=" + nonEnglishText);
		abandon(nonEnglishId);
	}

	@Test
	void ownerUc1_decline_and_owner_choice_route_to_staff() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int declinedId = startRequest(1, "Do not share this");
		postAction(declinedId, "decline", Map.of());
		assertWithStaff(declinedId, "DECLINED_CONSENT");
		abandon(declinedId);

		int ownerChoiceId = startRequest(1, "Please ask staff instead");
		advanceToReview(ownerChoiceId);
		postAction(ownerChoiceId, "staff-assistance", Map.of());
		assertWithStaff(ownerChoiceId, "OWNER_CHOICE");
		abandon(ownerChoiceId);
	}

	@Test
	void ownerUc1_edit_legs_return_to_consent() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		for (String startingState : List.of("AWAITING_CONSENT", "INTERPRETATION_FAILED", "INTERPRETED",
				"SUGGESTION_OFFERED")) {
			int id = requestInState(startingState);
			HttpResponse<String> edit = this.browser.get("/my/requests/" + id + "/edit", 200);
			this.browser.post("/my/requests/" + id + "/edit", edit, Map.of("requestText", "Edited request"), 302);
			assertRequest(id, "AWAITING_CONSENT", 1, "Edited request");
			assertThat(count("select count(*) from appointments where request_id = ? and status = 'HELD'", id))
				.isZero();
			abandon(id);
		}
	}

	@Test
	void ownerUc1_ai_failure_unavailability_restart_and_late_result_legs() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		for (String mode : List.of("unparseable", "zero-window", "understood-false")) {
			int id = startRequest(1, "[interpreter:" + mode + "] needs care");
			postAction(id, "consent", Map.of());
			awaitState(id, "INTERPRETATION_FAILED");
			assertThat(count("select count(*) from interpretation_failures where request_id = ?", id)).isOne();
			abandon(id);
		}

		int thirdFailure = startRequest(1, "[interpreter:unparseable] attempt one");
		for (int attempt = 1; attempt <= 3; attempt++) {
			postAction(thirdFailure, "consent", Map.of());
			awaitState(thirdFailure, "INTERPRETATION_FAILED");
			if (attempt < 3) {
				postAction(thirdFailure, "edit",
						Map.of("requestText", "[interpreter:unparseable] attempt " + (attempt + 1)));
			}
		}
		assertThat(this.browser.get("/my/requests/" + thirdFailure, 200).body()).containsIgnoringCase("staff");
		abandon(thirdFailure);

		for (String mode : List.of("transport", "never-completing")) {
			int id = startRequest(1, "[interpreter:" + mode + "]");
			postAction(id, "consent", Map.of());
			awaitState(id, "WITH_STAFF");
			assertWithStaff(id, "AI_UNAVAILABLE");
			abandon(id);
		}

		int late = startRequest(1, "[interpreter:late-success]");
		postAction(late, "consent", Map.of());
		postAction(late, "abandon", Map.of());
		awaitState(late, "ABANDONED");
		assertThat(count("select count(*) from interpretations where request_id = ?", late)).isZero();
	}

	@Test
	void uc3G14_realServerContinuesThroughStaffSuggestionCompletionAndDirectBookingNoShow() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int assisted = startRequest(1, "Surgery follow-up, mornings this week");
		advanceToReview(assisted);
		postAction(assisted, "staff-assistance", Map.of());
		assertWithStaff(assisted, "OWNER_CHOICE");

		HttpResponse<String> staffQueue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		this.browser.post("/staff/requests/" + assisted + "/suggest", staffQueue,
				Map.of("version", text(value("select version from scheduling_requests where id = ?", assisted)),
						"veterinarianId", "4", "date", "2026-09-04", "startTime", "10:00", "durationMinutes", "30",
						"reason", "Staff selected after reviewing the owner request"),
				302);
		assertRequestState(assisted, "SUGGESTION_OFFERED");
		Map<String, Object> assistedHold = onlyAppointment(assisted, "HELD");

		HttpResponse<String> ownerSuggestion = this.browser.loginAsNewSession("george", "george123",
				"/my/appointments");
		ownerSuggestion = this.browser.get("/my/requests/" + assisted, 200);
		this.browser.post("/my/requests/" + assisted + "/accept", ownerSuggestion, Map.of(), 302);
		assertRequestState(assisted, "ACCEPTED");
		int completedAppointment = ((Number) assistedHold.get("ID")).intValue();
		assertThat(value("select status from appointments where id = ?", completedAppointment)).isEqualTo("CONFIRMED");

		staffQueue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		this.browser.post("/staff/appointments/" + completedAppointment + "/complete", staffQueue,
				Map.of("description", "Post-surgery follow-up completed"), 302);
		assertThat(value("select status from appointments where id = ?", completedAppointment)).isEqualTo("COMPLETED");
		assertThat(row("select appointment_id, visit_date, description from visits where appointment_id = ?",
				completedAppointment))
			.containsEntry("APPOINTMENT_ID", completedAppointment)
			.containsEntry("DESCRIPTION", "Post-surgery follow-up completed");

		this.browser.loginAsNewSession("george", "george123", "/my/appointments");
		int declined = startRequest(1, "Please book without automated interpretation");
		postAction(declined, "decline", Map.of());
		assertWithStaff(declined, "DECLINED_CONSENT");

		staffQueue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		HttpResponse<String> declinedDetail = this.browser.get("/staff/requests/" + declined, 200);
		this.browser.post("/staff/requests/" + declined + "/interpretation", declinedDetail,
				Map.of("version", text(value("select version from scheduling_requests where id = ?", declined)),
						"careType", "GENERAL", "specialty", "", "specialtyLabel", "", "durationMinutes", "30",
						"preferredVetId", "", "preferredWindows", "FRIDAY 10:00 16:00", "allowedWindows", "",
						"excludedWindows", ""),
				302);
		staffQueue = this.browser.get("/staff/requests/" + declined, 200);
		this.browser.post("/staff/requests/" + declined + "/book", staffQueue,
				Map.of("version", text(value("select version from scheduling_requests where id = ?", declined)),
						"veterinarianId", "5", "date", "2026-09-04", "startTime", "11:00", "durationMinutes", "30",
						"reason", "Booked after consent was declined"),
				302);
		assertRequestState(declined, "ACCEPTED");
		Map<String, Object> directBooking = onlyAppointment(declined, "CONFIRMED");
		int noShowAppointment = ((Number) directBooking.get("ID")).intValue();
		staffQueue = this.browser.get("/staff/queue", 200);
		this.browser.post("/staff/appointments/" + noShowAppointment + "/no-show", staffQueue, Map.of(), 302);
		assertThat(value("select status from appointments where id = ?", noShowAppointment)).isEqualTo("NO_SHOW");
		assertThat(count("select count(*) from visits where appointment_id = ?", noShowAppointment)).isZero();
	}

	@Test
	void uc4_realServerStaffCreatesAuthorsResolvesAndOwnerSeesOutcomes() throws Exception {
		HttpResponse<String> queue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		assertThat(queue.body()).contains("Needs staff", "In progress", "Create staff request");

		HttpResponse<String> created = this.browser.post("/staff/requests", queue,
				Map.of("petId", "1", "requestText", "Staff-created radiology request"), 302);
		int bookedRequest = ((Number) value("select id from scheduling_requests where active_pet_id = 1")).intValue();
		assertThat(created.headers().firstValue("Location").orElseThrow()).endsWith("/staff/requests/" + bookedRequest);
		assertWithStaff(bookedRequest, "STAFF_CREATED");
		assertThat(count("select count(*) from interpretations where request_id = ?", bookedRequest)).isZero();
		assertThat(this.interpreter.getCallCount()).isZero();

		HttpResponse<String> detail = this.browser.follow(created, 200);
		assertThat(detail.body())
			.contains("George Franklin", "Leo", "Request created by staff", "No interpretation has been recorded")
			.doesNotContain("/staff/requests/" + bookedRequest + "/book");
		HttpResponse<String> authored = this.browser.post("/staff/requests/" + bookedRequest + "/interpretation",
				detail,
				Map.of("version", text(value("select version from scheduling_requests where id = ?", bookedRequest)),
						"careType", "SPECIALTY", "specialty", "radiology", "specialtyLabel", "", "durationMinutes",
						"30", "preferredVetId", "5", "preferredWindows", "MONDAY 09:00 10:00", "allowedWindows", "",
						"excludedWindows", ""),
				302);
		detail = this.browser.follow(authored, 200);
		assertThat(detail.body()).contains("Clinic staff interpretation", "radiology", "matches requested specialty",
				"specialty mismatch allowed", "/staff/requests/" + bookedRequest + "/book");

		this.browser.post("/staff/requests/" + bookedRequest + "/book", detail,
				Map.of("version", text(value("select version from scheduling_requests where id = ?", bookedRequest)),
						"veterinarianId", "4", "date", "2026-09-04", "startTime", "10:30", "durationMinutes", "30",
						"reason", "Staff override agreed with owner"),
				302);
		assertRequestState(bookedRequest, "ACCEPTED");
		assertThat(
				row("select status, vet_id, last_change_reason from appointments where request_id = ?", bookedRequest))
			.containsEntry("STATUS", "CONFIRMED")
			.containsEntry("VET_ID", 4)
			.containsEntry("LAST_CHANGE_REASON", "Staff override agreed with owner");
		HttpResponse<String> ownerAppointments = this.browser.loginAsNewSession("george", "george123",
				"/my/appointments");
		assertThat(ownerAppointments.body()).contains("2026-09-04", "Rafael Ortega",
				"Staff override agreed with owner");

		queue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		this.browser.post("/staff/requests", queue, Map.of("petId", "2", "requestText", "Staff suggestion request"),
				302);
		int suggestionRequest = ((Number) value("select id from scheduling_requests where active_pet_id = 2"))
			.intValue();
		detail = this.browser.get("/staff/requests/" + suggestionRequest, 200);
		this.browser.post("/staff/requests/" + suggestionRequest + "/interpretation", detail, Map.of("version",
				text(value("select version from scheduling_requests where id = ?", suggestionRequest)), "careType",
				"GENERAL", "specialty", "", "specialtyLabel", "", "durationMinutes", "30", "preferredVetId", "",
				"preferredWindows", "TUESDAY 09:00 12:00", "allowedWindows", "", "excludedWindows", ""), 302);
		detail = this.browser.get("/staff/requests/" + suggestionRequest, 200);
		this.browser.post("/staff/requests/" + suggestionRequest + "/suggest", detail,
				Map.of("version",
						text(value("select version from scheduling_requests where id = ?", suggestionRequest)),
						"veterinarianId", "1", "date", "2026-09-08", "startTime", "10:00", "durationMinutes", "30",
						"reason", "Staff selected this option"),
				302);
		assertRequestState(suggestionRequest, "SUGGESTION_OFFERED");
		assertThat(row("select status, last_change_reason from appointments where request_id = ?", suggestionRequest))
			.containsEntry("STATUS", "HELD")
			.containsEntry("LAST_CHANGE_REASON", "Staff selected this option");

		HttpResponse<String> duplicate = this.browser.post("/staff/requests", this.browser.get("/staff/queue", 200),
				Map.of("petId", "2", "requestText", "Must not be created"), 302);
		assertThat(duplicate.headers().firstValue("Location").orElseThrow())
			.endsWith("/staff/requests/" + suggestionRequest);
		assertThat(count("select count(*) from scheduling_requests where pet_id = 2 and active_pet_id is not null"))
			.isOne();

		HttpResponse<String> betty = this.browser.loginAsNewSession("betty", "betty123", "/my/appointments");
		assertThat(this.browser.get("/my/requests/" + suggestionRequest, 200).body()).contains("Suggested appointment",
				"2026-09-08", "James Carter");
		assertThat(betty.body()).contains("Suggestion offered");
	}

	@Test
	void ownerUc1_other_and_no_slots_legs() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int other = startRequest(1, "[interpreter:other] exotic animal");
		advanceToReview(other);
		postAction(other, "confirm", Map.of());
		assertWithStaff(other, "UNMATCHED_SPECIALTY");
		abandon(other);

		int noSlots = startRequest(1, "[matcher:no-slots] surgery");
		advanceToReview(noSlots);
		postAction(noSlots, "confirm", Map.of());
		assertWithStaff(noSlots, "NO_SLOTS");
	}

	@Test
	@Tag("AC-74")
	void ownerUc1_another_option_and_hold_release_legs() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int id = requestInState("SUGGESTION_OFFERED");
		Map<String, Object> first = onlyAppointment(id, "HELD");
		postAction(id, "another-option", Map.of());
		Map<String, Object> second = onlyAppointment(id, "HELD");
		assertThat(second.get("ID")).isNotEqualTo(first.get("ID"));
		assertThat(List.of(second.get("VET_ID"), second.get("APPOINTMENT_DATE"), second.get("START_TIME")))
			.isNotEqualTo(List.of(first.get("VET_ID"), first.get("APPOINTMENT_DATE"), first.get("START_TIME")));
		assertThat(count("select count(*) from appointments where id = ?", first.get("ID"))).isZero();

		HttpResponse<String> queue = this.browser.loginAsNewSession("staff", "staff123", "/staff/queue");
		this.browser.post("/staff/requests/" + id + "/release-hold", queue, Map.of("version",
				text(value("select version from scheduling_requests where id = ?", id)), "reason", "Schedule changed"),
				302);
		assertWithStaff(id, "HOLD_RELEASED");
		assertThat(value("select staff_reason from scheduling_requests where id = ?", id))
			.isEqualTo("Schedule changed");
		assertThat(count("select count(*) from appointments where request_id = ? and status = 'HELD'", id)).isZero();
		this.browser.loginAsNewSession("george", "george123", "/my/appointments");
		abandon(id);

		int unavailableId = requestInState("SUGGESTION_OFFERED");
		Map<String, Object> unavailableHold = onlyAppointment(unavailableId, "HELD");
		this.jdbc.update(
				"insert into appointments(request_id, pet_id, vet_id, appointment_date, start_time, end_time, status, created_date, created_time) values (null, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?)",
				2, unavailableHold.get("VET_ID"), unavailableHold.get("APPOINTMENT_DATE"),
				unavailableHold.get("START_TIME"), unavailableHold.get("END_TIME"),
				unavailableHold.get("APPOINTMENT_DATE"), unavailableHold.get("START_TIME"));
		HttpResponse<String> staleSuggestion = this.browser.get("/my/requests/" + unavailableId, 200);
		HttpResponse<String> replacement = this.browser.post("/my/requests/" + unavailableId + "/accept",
				staleSuggestion, Map.of(), 302);
		assertThat(replacement.headers().firstValue("Location").orElseThrow()).contains("notice=slotChanged");
		assertRequestState(unavailableId, "SUGGESTION_OFFERED");
		assertThat(count("select count(*) from appointments where id = ?", unavailableHold.get("ID"))).isZero();
		Map<String, Object> replacementHold = onlyAppointment(unavailableId, "HELD");
		assertThat(replacementHold.get("ID")).isNotEqualTo(unavailableHold.get("ID"));
		assertRenderedSlot(this.browser.follow(replacement, 200).body(), replacementHold);
	}

	@Test
	void ownerUc1_wrong_state_leg_returns_current_page() throws Exception {
		this.browser.login("george", "george123", "/my/appointments");
		int id = startRequest(1, "Wrong-state checks");
		Map<String, Object> before = row("select * from scheduling_requests where id = ?", id);
		for (String action : List.of("confirm", "accept", "another-option")) {
			HttpResponse<String> response = postAction(id, action, Map.of(), 200);
			assertThat(response.statusCode()).isBetween(200, 399);
			assertThat(row("select * from scheduling_requests where id = ?", id)).isEqualTo(before);
		}
	}

	@Test
	@Tag("AC-1")
	@Tag("AC-7")
	@Tag("AC-8")
	@Tag("AC-15")
	void userSignsInAndNavigatesByRole() throws Exception {
		Browser anonBrowser = new Browser(this.port);
		HttpResponse<String> anonRedirect = anonBrowser.get("/my/appointments", 302);
		assertThat(anonRedirect.headers().firstValue("Location").orElse("")).contains("/login");

		Browser ownerBrowser = new Browser(this.port);
		ownerBrowser.login("george", "george123", "/my/appointments");
		HttpResponse<String> ownerHome = ownerBrowser.get("/", 302);
		assertThat(ownerHome.headers().firstValue("Location").orElse("")).contains("/my/appointments");

		Browser staffBrowser = new Browser(this.port);
		staffBrowser.login("staff", "staff123", "/staff/queue");
		HttpResponse<String> staffHome = staffBrowser.get("/", 302);
		assertThat(staffHome.headers().firstValue("Location").orElse("")).contains("/staff/queue");

		HttpResponse<String> ownerPage = ownerBrowser.get("/my/appointments", 200);
		assertThat(ownerPage.body()).contains("My pets", "My appointments", "george", "/logout");
		assertThat(ownerPage.body()).doesNotContain("Scheduling queue", "Calendar", "Clinic settings");

		HttpResponse<String> staffPage = staffBrowser.get("/staff/queue", 200);
		assertThat(staffPage.body()).contains("Scheduling queue", "Calendar", "Clinic settings", "staff", "/logout");
		assertThat(staffPage.body()).doesNotContain("My pets", "My appointments");

		HttpResponse<String> petsPage = ownerBrowser.get("/my/pets", 200);
		assertThat(petsPage.body()).contains("My pets", "george");
		assertThat(petsPage.body()).doesNotContain("Basil", "Samantha");

		HttpResponse<String> apptsPage = ownerBrowser.get("/my/appointments", 200);
		assertThat(apptsPage.body()).contains("My appointments");

		HttpResponse<String> ownerLogout = ownerBrowser.post("/logout", ownerPage, Map.of(), 302);
		assertThat(ownerLogout.headers().firstValue("Location").orElse("")).contains("/login");
		HttpResponse<String> postLogout = ownerBrowser.get("/my/appointments", 302);
		assertThat(postLogout.headers().firstValue("Location").orElse("")).contains("/login");
	}

	@Test
	void userUc5_wrong_credentials_leg() throws Exception {
		Browser badBrowser = new Browser(this.port);
		HttpResponse<String> loginPage = badBrowser.get("/login", 200);
		HttpResponse<String> failed = badBrowser.post("/login", loginPage,
				Map.of("username", "george", "password", "wrong-password"), 302);
		assertThat(failed.headers().firstValue("Location").orElse("")).contains("/login?error");

		HttpResponse<String> errorPage = badBrowser.follow(failed, 200);
		assertThat(errorPage.body()).contains("Invalid username or password");
	}

	@Test
	void userUc5_owner_staff_and_foreign_denial_legs() throws Exception {
		Browser ownerBrowser = new Browser(this.port);
		ownerBrowser.login("george", "george123", "/my/appointments");
		ownerBrowser.get("/staff/queue", 403);

		Browser staffBrowser = new Browser(this.port);
		staffBrowser.login("staff", "staff123", "/staff/queue");
		staffBrowser.get("/my/appointments", 403);
		staffBrowser.get("/my/pets", 403);

		HttpResponse<String> form = ownerBrowser.get("/my/requests/new", 200);
		int georgeReqId = requestId(ownerBrowser.post("/my/requests/new", form,
				Map.of("petId", "1", "requestText", "Checkup for Leo"), 302));

		Browser bettyBrowser = new Browser(this.port);
		bettyBrowser.login("betty", "betty123", "/my/appointments");

		HttpResponse<String> foreignResp = bettyBrowser.get("/my/requests/" + georgeReqId, 404);
		assertThat(foreignResp.statusCode()).isEqualTo(404);
		assertThat(foreignResp.body()).doesNotContain("Leo", "Checkup for Leo");

		HttpResponse<String> nonExistentResp = bettyBrowser.get("/my/requests/999999", 404);
		assertThat(nonExistentResp.statusCode()).isEqualTo(404);

		HttpResponse<String> missingPage = bettyBrowser.get("/my/appointments", 200);
		bettyBrowser.post("/my/requests/" + georgeReqId + "/abandon", missingPage, Map.of(), 404);
	}

	@Test
	void userUc5_anonymous_health_leg() throws Exception {
		Browser anonBrowser = new Browser(this.port);
		HttpResponse<String> health = anonBrowser.get("/actuator/health", 200);
		assertThat(health.body()).contains("UP");
	}

	@Test
	@Tag("AC-25")
	void userUc5_staff_stock_pages_leg() throws Exception {
		Browser staffBrowser = new Browser(this.port);
		staffBrowser.login("staff", "staff123", "/staff/queue");

		staffBrowser.get("/owners/find", 200);
		staffBrowser.get("/owners?lastName=", 200);
		staffBrowser.get("/owners/1", 200);
		staffBrowser.get("/vets", 200);
		staffBrowser.get("/vets.html", 200);
	}

	private int startRequest(int petId, String requestText) throws Exception {
		HttpResponse<String> form = this.browser.get("/my/requests/new", 200);
		return requestId(this.browser.post("/my/requests/new", form,
				Map.of("petId", Integer.toString(petId), "requestText", requestText), 302));
	}

	private int requestInState(String state) throws Exception {
		String text = "INTERPRETATION_FAILED".equals(state) ? "[interpreter:unparseable] Request for " + state
				: "Request for " + state;
		int id = startRequest(1, text);
		if (!"AWAITING_CONSENT".equals(state)) {
			postAction(id, "consent", Map.of());
		}
		if ("INTERPRETATION_FAILED".equals(state)) {
			awaitState(id, state);
		}
		else if (List.of("INTERPRETED", "SUGGESTION_OFFERED").contains(state)) {
			awaitState(id, "INTERPRETED");
			if ("SUGGESTION_OFFERED".equals(state)) {
				postAction(id, "confirm", Map.of());
			}
		}
		return id;
	}

	private void advanceToReview(int id) throws Exception {
		postAction(id, "consent", Map.of());
		awaitState(id, "INTERPRETED");
	}

	private HttpResponse<String> postAction(int id, String action, Map<String, String> values) throws Exception {
		return postAction(id, action, values, 302);
	}

	private HttpResponse<String> postAction(int id, String action, Map<String, String> values, int expectedStatus)
			throws Exception {
		HttpResponse<String> page = this.browser.get("/my/requests/" + id, 200);
		return this.browser.post("/my/requests/" + id + "/" + action, page, values, expectedStatus);
	}

	private void abandon(int id) throws Exception {
		postAction(id, "abandon", Map.of());
		assertRequestState(id, "ABANDONED");
	}

	private void awaitState(int id, String expected) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(125).toNanos();
		String actual;
		do {
			actual = text(value("select state from scheduling_requests where id = ?", id));
			if (expected.equals(actual)) {
				return;
			}
			Thread.sleep(50);
		}
		while (System.nanoTime() < deadline);
		assertThat(actual).isEqualTo(expected);
	}

	private void assertRequest(int id, String state, Integer activePetId, String requestText) {
		Map<String, Object> request = row("select * from scheduling_requests where id = ?", id);
		assertThat(request.get("STATE")).isEqualTo(state);
		assertThat(request.get("ACTIVE_PET_ID")).isEqualTo(activePetId);
		assertThat(request.get("REQUEST_TEXT")).isEqualTo(requestText);
	}

	private void assertRequestState(int id, String state) {
		assertThat(value("select state from scheduling_requests where id = ?", id)).isEqualTo(state);
	}

	private void assertWithStaff(int id, String reason) {
		Map<String, Object> request = row("select state, with_staff_reason from scheduling_requests where id = ?", id);
		assertThat(request).containsEntry("STATE", "WITH_STAFF").containsEntry("WITH_STAFF_REASON", reason);
	}

	private Map<String, Object> onlyAppointment(int requestId, String status) {
		List<Map<String, Object>> rows = this.jdbc
			.queryForList("select * from appointments where request_id = ? and status = ?", requestId, status);
		assertThat(rows).hasSize(1);
		return rows.get(0);
	}

	private void assertRenderedSlot(String body, Map<String, Object> slot) {
		assertThat(body).contains(text(slot.get("APPOINTMENT_DATE")), shortTime(slot.get("START_TIME")),
				shortTime(slot.get("END_TIME")), text(slot.get("RANK_REASON")), "suggestion-specialty");
		assertThat(slot).containsKeys("VET_ID", "APPOINTMENT_DATE", "START_TIME", "END_TIME", "RANK_REASON");
	}

	private String shortTime(Object value) {
		String text = text(value);
		return text.length() >= 5 ? text.substring(0, 5) : text;
	}

	private Map<String, Object> row(String sql, Object... arguments) {
		return this.jdbc.queryForMap(sql, arguments);
	}

	private Object value(String sql, Object... arguments) {
		return this.jdbc.queryForObject(sql, Object.class, arguments);
	}

	private long count(String sql, Object... arguments) {
		return this.jdbc.queryForObject(sql, Long.class, arguments);
	}

	private static int requestId(HttpResponse<String> response) {
		String location = response.headers().firstValue("Location").orElseThrow();
		Matcher matcher = REQUEST_LOCATION.matcher(location);
		assertThat(matcher.matches()).as("request redirect %s", location).isTrue();
		return Integer.parseInt(matcher.group(1));
	}

	private static String text(Object value) {
		return String.valueOf(value);
	}

	private static final class Browser {

		private final URI baseUri;

		private HttpClient client;

		private Browser(int port) {
			this.baseUri = URI.create("http://localhost:" + port);
			newSession();
		}

		private void newSession() {
			CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			this.client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		}

		private HttpResponse<String> loginAsNewSession(String username, String password, String landing)
				throws Exception {
			newSession();
			login(username, password, landing);
			return get(landing, 200);
		}

		private void login(String username, String password, String landing) throws Exception {
			HttpResponse<String> page = get("/login", 200);
			HttpResponse<String> response = post("/login", page, Map.of("username", username, "password", password),
					302);
			assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith(landing);
		}

		private HttpResponse<String> get(String path, int expectedStatus) throws Exception {
			HttpRequest request = HttpRequest.newBuilder(resolve(path)).GET().timeout(Duration.ofSeconds(130)).build();
			return send(request, expectedStatus);
		}

		private HttpResponse<String> post(String path, HttpResponse<String> page, Map<String, String> values,
				int expectedStatus) throws Exception {
			Map<String, String> form = new LinkedHashMap<>(values);
			csrf(page.body()).ifPresent(token -> form.put("_csrf", token));
			String encoded = form.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
			HttpRequest request = HttpRequest.newBuilder(resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(encoded))
				.timeout(Duration.ofSeconds(130))
				.build();
			return send(request, expectedStatus);
		}

		private HttpResponse<String> follow(HttpResponse<String> response, int expectedStatus) throws Exception {
			return get(response.headers().firstValue("Location").orElseThrow(), expectedStatus);
		}

		private HttpResponse<String> send(HttpRequest request, int expectedStatus)
				throws IOException, InterruptedException {
			HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).as("%s %s body=%s", request.method(), request.uri(), response.body())
				.isEqualTo(expectedStatus);
			return response;
		}

		private URI resolve(String path) {
			URI candidate = URI.create(path);
			return candidate.isAbsolute() ? candidate : this.baseUri.resolve(path);
		}

		private static java.util.Optional<String> csrf(String body) {
			Matcher matcher = CSRF.matcher(body);
			if (!matcher.find()) {
				return java.util.Optional.empty();
			}
			return java.util.Optional.ofNullable(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
		}

		private static String encode(String value) {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		}

	}

}
