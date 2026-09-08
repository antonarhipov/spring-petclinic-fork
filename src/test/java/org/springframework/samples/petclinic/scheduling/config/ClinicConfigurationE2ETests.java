package org.springframework.samples.petclinic.scheduling.config;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.support.TestClockConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClinicConfigurationE2ETests {

	private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 10);

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ClinicConfigurationService configurations;

	private Browser browser;

	@BeforeEach
	void openBrowser() {
		this.browser = new Browser(this.port);
	}

	@Test
	void uc7MainStaffMaintainsConfigurationAndReceivesAffectedRequestsThroughRealHttp() throws Exception {
		int requestId = insertHeldRequest();
		int heldId = insertHeldAppointment(requestId);
		this.browser.login("staff", "staff123");
		HttpResponse<String> page = this.browser.get("/staff/settings");
		assertThat(page.statusCode()).isEqualTo(200);
		assertThat(page.body())
			.contains("Clinic settings", "Opening hours", "Veterinarian availability", "1,MONDAY,09:00,17:00",
					"1,2026-09-15", "Europe/Amsterdam", "Derived from each weekday&#39;s opening hours",
					">staff</span>", ">Logout</button>")
			.doesNotContain("name=\"morningStart\"", "name=\"eveningEnd\"");

		ClinicConfigurationForm form = ClinicConfigurationForm.from(this.configurations.current());
		form.setClosures(THURSDAY.toString());
		HttpResponse<String> saved = this.browser.post("/staff/settings", page, values(form));

		assertThat(saved.statusCode()).isEqualTo(200);
		assertThat(saved.body()).contains("The configuration was saved", "Affected scheduling requests",
				"/staff/requests/" + requestId, "Leo", "Rafael Ortega", "2026-09-10");
		assertThat(this.jdbc.queryForObject("select count(*) from clinic_closures where closure_date = ?",
				Integer.class, Date.valueOf(THURSDAY)))
			.isOne();
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where id = ?", Integer.class, heldId))
			.isZero();
		assertThat(this.jdbc.queryForMap("select state, with_staff_reason from scheduling_requests where id = ?",
				requestId))
			.containsEntry("STATE", "WITH_STAFF")
			.containsEntry("WITH_STAFF_REASON", "SCHEDULE_CHANGED");
		assertThat(this.browser.get("/staff/calendar?date=2026-09-10").body())
			.contains("The clinic is closed on this day.");
	}

	private Map<String, String> values(ClinicConfigurationForm form) {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("bookingHorizonDays", form.getBookingHorizonDays().toString());
		values.put("minimumLeadDays", form.getMinimumLeadDays().toString());
		values.put("minimumDurationMinutes", form.getMinimumDurationMinutes().toString());
		values.put("defaultDurationMinutes", form.getDefaultDurationMinutes().toString());
		values.put("maximumDurationMinutes", form.getMaximumDurationMinutes().toString());
		values.put("timeZone", form.getTimeZone());
		values.put("workingPeriods", form.getWorkingPeriods());
		values.put("exceptions", form.getExceptions());
		values.put("leavePeriods", form.getLeavePeriods());
		values.put("closures", form.getClosures());
		for (int index = 0; index < form.getOpeningHours().size(); index++) {
			ClinicConfigurationForm.OpeningHoursValue hours = form.getOpeningHours().get(index);
			values.put("openingHours[" + index + "].weekday", hours.getWeekday().name());
			values.put("openingHours[" + index + "].closed", Boolean.toString(hours.isClosed()));
			values.put("openingHours[" + index + "].openTime", hours.getOpenTime());
			values.put("openingHours[" + index + "].closeTime", hours.getCloseTime());
		}
		return values;
	}

	private int insertHeldRequest() {
		int id = nextId("scheduling_requests");
		this.jdbc.update("""
				insert into scheduling_requests
				(id, pet_id, active_pet_id, request_text, state, created_date, created_time, failure_count, version)
				values (?, 1, 1, 'Configuration E2E fixture', 'SUGGESTION_OFFERED', '2026-09-07', '09:00:00', 0, 0)
				""", id);
		return id;
	}

	private int insertHeldAppointment(int requestId) {
		int id = nextId("appointments");
		this.jdbc.update("""
				insert into appointments
				(id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status,
				 held_date, held_time, created_date, created_time)
				values (?, ?, 1, 4, ?, ?, ?, 'HELD', '2026-09-07', '09:00:00', '2026-09-07', '09:00:00')
				""", id, requestId, Date.valueOf(THURSDAY), Time.valueOf(LocalTime.of(9, 0)),
				Time.valueOf(LocalTime.of(9, 30)));
		return id;
	}

	private int nextId(String table) {
		return this.jdbc.queryForObject("select coalesce(max(id), 0) + 1 from " + table, Integer.class);
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

		private void login(String username, String password) throws Exception {
			HttpResponse<String> login = get("/login");
			HttpResponse<String> response = post("/login", login, Map.of("username", username, "password", password));
			assertThat(response.statusCode()).isEqualTo(302);
		}

		private HttpResponse<String> get(String path) throws Exception {
			return this.client.send(HttpRequest.newBuilder(this.baseUri.resolve(path)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
		}

		private HttpResponse<String> post(String path, HttpResponse<String> source, Map<String, String> values)
				throws Exception {
			Map<String, String> submitted = new LinkedHashMap<>(values);
			submitted.put("_csrf", csrf(source.body()));
			String form = submitted.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(java.util.stream.Collectors.joining("&"));
			HttpRequest request = HttpRequest.newBuilder(this.baseUri.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
			return this.client.send(request, HttpResponse.BodyHandlers.ofString());
		}

		private String csrf(String html) {
			Matcher matcher = CSRF.matcher(html);
			if (!matcher.find()) {
				throw new IllegalStateException("CSRF field not found");
			}
			return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		}

		private String encode(String value) {
			return URLEncoder.encode(value, StandardCharsets.UTF_8);
		}

	}

}
