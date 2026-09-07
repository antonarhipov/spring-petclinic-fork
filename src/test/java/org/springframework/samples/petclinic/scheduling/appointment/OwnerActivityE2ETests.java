package org.springframework.samples.petclinic.scheduling.appointment;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Outside-in executable specification for UC-2 and its UC-1 dependency. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OwnerActivityE2ETests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	private Browser browser;

	@BeforeEach
	void openBrowser() {
		this.browser = new Browser(this.port);
	}

	@Test
	void uc2MainOwnerReviewsOnlyCompleteOwnedActivityThroughRealHttp() throws Exception {
		insertAppointment(1, 1, TODAY.minusDays(1), LocalTime.of(10, 0), "COMPLETED", null);
		int upcoming = insertAppointment(1, 3, TODAY.plusDays(1), LocalTime.of(11, 15), "CONFIRMED",
				"Moved for emergency coverage");
		insertAppointment(1, 2, TODAY.plusDays(2), LocalTime.of(13, 0), "CANCELLED", null);
		int requestId = insertInterpretedRequest(1, 2);
		DatabaseSnapshot before = snapshot();

		this.browser.login("george", "george123", "/my/appointments");
		HttpResponse<String> petsResponse = this.browser.get("/my/pets");
		assertThat(petsResponse.statusCode()).isEqualTo(200);
		assertThat(petsResponse.body())
			.contains("George", "Franklin", "110 W. Liberty St.", "Madison", "6085551023", "Leo", "2010-09-07", "cat")
			.doesNotContain("Betty", "Basil", "/owners/1/edit", "/pets/new");

		HttpResponse<String> activityResponse = this.browser.get("/my/appointments");
		assertThat(activityResponse.statusCode()).isEqualTo(200);
		assertThat(activityResponse.body())
			.contains("Leo", "2026-09-06", "Completed", "2026-09-08", "11:15-11:45", "Linda Douglas",
					"dentistry, surgery", "Confirmed", "Moved for emergency coverage", "2026-09-09", "Cancelled",
					"Ready for confirmation", "AI interpretation", "surgery", "Helen Leary", "radiology",
					"/my/requests/" + requestId, "/my/appointments/" + upcoming + "/cancel")
			.doesNotContain("Betty", "Basil", "/vets.html", "Start a request");

		HttpResponse<String> detailResponse = this.browser.get("/my/requests/" + requestId);
		assertThat(detailResponse.statusCode()).isEqualTo(200);
		assertThat(detailResponse.body()).contains("AI interpretation", "surgery", "Helen Leary");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void uc2MainOwnerCancelsDisplayedUpcomingAppointmentThroughRealHttp() throws Exception {
		int appointmentId = insertAppointment(1, 2, TODAY.plusDays(1), LocalTime.of(11, 15), "CONFIRMED", null);
		DatabaseSnapshot before = snapshot();
		this.browser.login("george", "george123", "/my/appointments");

		HttpResponse<String> page = this.browser.get("/my/appointments");
		assertThat(page.body()).contains("/my/appointments/" + appointmentId + "/cancel", "method=\"post\"");
		HttpResponse<String> cancelled = this.browser.post("/my/appointments/" + appointmentId + "/cancel", page,
				Map.of());
		assertThat(cancelled.statusCode()).isEqualTo(302);
		assertThat(cancelled.headers().firstValue("Location"))
			.hasValue("http://localhost:" + this.port + "/my/appointments");

		Map<String, Object> row = this.jdbc.queryForMap("select * from appointments where id = ?", appointmentId);
		assertThat(row).containsEntry("STATUS", "CANCELLED")
			.containsEntry("CANCELLED_BY", "OWNER")
			.containsEntry("CANCELLED_DATE", Date.valueOf(TODAY))
			.containsEntry("CANCELLED_TIME", Time.valueOf(LocalTime.of(9, 0)));
		DatabaseSnapshot after = snapshot();
		assertThat(after.owners()).isEqualTo(before.owners());
		assertThat(after.pets()).isEqualTo(before.pets());
		assertThat(after.requests()).isEqualTo(before.requests());
		assertThat(after.interpretations()).isEqualTo(before.interpretations());
		assertThat(after.visits()).isEqualTo(before.visits());
		assertThat(after.appointments()).hasSameSizeAs(before.appointments());
		assertThat(this.browser.get("/my/appointments").body())
			.contains("data-appointment-id=\"" + appointmentId + "\"", "Cancelled")
			.doesNotContain("/my/appointments/" + appointmentId + "/cancel");
	}

	@Test
	void uc2ExtensionOwnerWithoutPetsSeesReadOnlyEmptyStatesThroughRealHttp() throws Exception {
		int ownerId = insertOwnerWithoutPets();
		this.browser.login("nopets", "george123", "/my/appointments");

		String pets = this.browser.get("/my/pets").body();
		assertThat(pets).contains("Owner details", "Empty", "Owner", "No address", "Nowhere", "0000000000", "No pets")
			.doesNotContain("data-pet-id", "/my/requests/new", "Start a request", "Resume", "Cancel appointment");
		String appointments = this.browser.get("/my/appointments").body();
		assertThat(appointments).contains("No pets")
			.doesNotContain("data-pet-id", "/my/requests/new", "Start a request", "Resume", "Cancel appointment");
		assertThat(this.jdbc.queryForObject("select count(*) from pets where owner_id = ?", Integer.class, ownerId))
			.isZero();
	}

	@Test
	void uc2ExtensionForeignAndUnknownOwnerActivityHaveTheSame404AndNoSideEffect() throws Exception {
		int foreignRequest = insertAwaitingRequest(2, "Betty private request");
		int foreignAppointment = insertAppointment(2, 2, TODAY.plusDays(1), LocalTime.of(14, 0), "CONFIRMED",
				"Betty private reason");
		DatabaseSnapshot before = snapshot();
		this.browser.login("george", "george123", "/my/appointments");

		HttpResponse<String> foreignPet = this.browser.get("/my/requests/new?petId=2");
		HttpResponse<String> unknownPet = this.browser.get("/my/requests/new?petId=999999");
		assertThat(foreignPet.statusCode()).isEqualTo(404);
		assertThat(unknownPet.statusCode()).isEqualTo(404);
		assertThat(normalizeCsrf(foreignPet.body())).isEqualTo(normalizeCsrf(unknownPet.body()));

		HttpResponse<String> foreign = this.browser.get("/my/requests/" + foreignRequest);
		HttpResponse<String> unknown = this.browser.get("/my/requests/999999");
		assertThat(foreign.statusCode()).isEqualTo(404);
		assertThat(unknown.statusCode()).isEqualTo(404);
		assertThat(normalizeCsrf(foreign.body())).isEqualTo(normalizeCsrf(unknown.body()))
			.doesNotContain("Betty private request", "Betty private reason", "Basil", "Betty");
		HttpResponse<String> appointmentsPage = this.browser.get("/my/appointments");
		HttpResponse<String> foreignAppointmentResponse = this.browser
			.post("/my/appointments/" + foreignAppointment + "/cancel", appointmentsPage, Map.of());
		HttpResponse<String> unknownAppointmentResponse = this.browser.post("/my/appointments/999999/cancel",
				appointmentsPage, Map.of());
		assertThat(foreignAppointmentResponse.statusCode()).isEqualTo(404);
		assertThat(unknownAppointmentResponse.statusCode()).isEqualTo(404);
		assertThat(normalizeCsrf(foreignAppointmentResponse.body()))
			.isEqualTo(normalizeCsrf(unknownAppointmentResponse.body()))
			.doesNotContain("Betty private reason", "Basil", "Betty");
		assertThat(this.browser.get("/my/appointments").body()).doesNotContain("Betty private request",
				"Betty private reason", "Basil", "Betty");
		assertThat(snapshot()).isEqualTo(before);
	}

	private int insertOwnerWithoutPets() {
		this.jdbc.update(
				"insert into owners (first_name, last_name, address, city, telephone) values ('Empty', 'Owner', 'No address', 'Nowhere', '0000000000')");
		int ownerId = this.jdbc.queryForObject("select max(id) from owners", Integer.class);
		this.jdbc.update("""
				insert into user_accounts (username, password_hash, role, owner_id)
				select 'nopets', password_hash, 'OWNER', ? from user_accounts where username = 'george'
				""", ownerId);
		return ownerId;
	}

	private int insertAwaitingRequest(int petId, String text) {
		this.jdbc.update("""
				insert into scheduling_requests
				(pet_id, active_pet_id, request_text, state, failure_count, created_date, created_time, version)
				values (?, ?, ?, 'AWAITING_CONSENT', 0, ?, ?, 0)
				""", petId, petId, text, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 0)));
		return this.jdbc.queryForObject("select max(id) from scheduling_requests", Integer.class);
	}

	private int insertInterpretedRequest(int petId, int preferredVetId) {
		int requestId = insertAwaitingRequest(petId, "Surgery follow-up");
		this.jdbc.update("update scheduling_requests set state = 'INTERPRETED' where id = ?", requestId);
		this.jdbc.update("""
				insert into interpretations
				(request_id, understood, care_type, specialty, duration_minutes, preferred_vet_id, origin,
				 raw_json, model_tag, prompt_version, created_date, created_time)
				values (?, true, 'SPECIALTY', 'surgery', 30, ?, 'AI', '{}', 'deterministic', 'v1', ?, ?)
				""", requestId, preferredVetId, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 1)));
		int interpretationId = this.jdbc.queryForObject("select max(id) from interpretations", Integer.class);
		this.jdbc.update("update scheduling_requests set current_interpretation_id = ? where id = ?", interpretationId,
				requestId);
		return requestId;
	}

	private int insertAppointment(int petId, int vetId, LocalDate date, LocalTime start, String status, String reason) {
		LocalTime end = start.plusMinutes(30);
		this.jdbc.update("""
				insert into appointments
				(pet_id, vet_id, appointment_date, start_time, end_time, status,
				 last_change_reason, last_changed_by, created_date, created_time)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", petId, vetId, Date.valueOf(date), Time.valueOf(start), Time.valueOf(end), status, reason,
				reason == null ? null : "staff", Date.valueOf(TODAY), Time.valueOf(LocalTime.of(8, 0)));
		return this.jdbc.queryForObject("select max(id) from appointments", Integer.class);
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(this.jdbc.queryForList("select * from owners order by id"),
				this.jdbc.queryForList("select * from pets order by id"),
				this.jdbc.queryForList("select * from scheduling_requests order by id"),
				this.jdbc.queryForList("select * from interpretations order by id"),
				this.jdbc.queryForList("select * from appointments order by id"),
				this.jdbc.queryForList("select * from visits order by id"));
	}

	private String normalizeCsrf(String body) {
		return body.replaceAll("(name=\"_csrf\" value=\")[^\"]+", "$1<token>");
	}

	private record DatabaseSnapshot(List<Map<String, Object>> owners, List<Map<String, Object>> pets,
			List<Map<String, Object>> requests, List<Map<String, Object>> interpretations,
			List<Map<String, Object>> appointments, List<Map<String, Object>> visits) {
	}

	private static final class Browser {

		private final URI baseUri;

		private final HttpClient client;

		private Browser(int port) {
			this.baseUri = URI.create("http://localhost:" + port);
			CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
			this.client = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		}

		private HttpResponse<String> get(String path) throws Exception {
			return this.client.send(HttpRequest.newBuilder(this.baseUri.resolve(path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private void login(String username, String password, String expectedPath) throws Exception {
			HttpResponse<String> page = get("/login");
			HttpResponse<String> response = post("/login", page, Map.of("username", username, "password", password));
			assertThat(response.statusCode()).isEqualTo(302);
			assertThat(response.headers().firstValue("Location"))
				.hasValueSatisfying(location -> assertThat(location).endsWith(expectedPath));
		}

		private HttpResponse<String> post(String path, HttpResponse<String> page, Map<String, String> values)
				throws Exception {
			Map<String, String> form = new LinkedHashMap<>(values);
			form.put("_csrf", csrf(page.body()));
			String body = form.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.reduce((left, right) -> left + "&" + right)
				.orElse("");
			HttpRequest request = HttpRequest.newBuilder(this.baseUri.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
			return this.client.send(request, HttpResponse.BodyHandlers.ofString());
		}

		private String csrf(String html) {
			var matcher = CSRF.matcher(html);
			if (!matcher.find()) {
				throw new IllegalStateException("No CSRF token in page");
			}
			return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		}

		private String encode(String value) {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		}

	}

}
