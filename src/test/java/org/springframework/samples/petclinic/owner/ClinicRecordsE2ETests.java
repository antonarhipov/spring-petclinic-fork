package org.springframework.samples.petclinic.owner;

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
import java.util.regex.Matcher;
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

/** Outside-in executable specification for UC-8 and its UC-1 dependency. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClinicRecordsE2ETests {

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
	void uc8MainStaffMaintainsOwnersPetsAndVeterinarianDirectoryThroughRealHttp() throws Exception {
		this.browser.login("staff", "staff123");

		HttpResponse<String> search = this.browser.get("/owners?lastName=Franklin");
		assertThat(search.statusCode()).isEqualTo(302);
		assertThat(search.headers().firstValue("Location"))
			.hasValueSatisfying(location -> assertThat(location).endsWith("/owners/1"));
		assertThat(this.browser.get("/owners/1").body()).contains("George Franklin", "110 W. Liberty St.", "Leo",
				"2010-09-07");

		HttpResponse<String> newOwner = this.browser.get("/owners/new");
		HttpResponse<String> ownerCreated = this.browser.post("/owners/new", newOwner, Map.of("firstName", "Uma",
				"lastName", "CaseEight", "address", "8 Contract Lane", "city", "Utrecht", "telephone", "0612345678"));
		assertThat(ownerCreated.statusCode()).isEqualTo(302);
		int ownerId = idFromRedirect(ownerCreated);
		assertThat(this.browser.get("/owners/" + ownerId).body()).contains("Uma CaseEight", "8 Contract Lane",
				"Utrecht", "0612345678");

		HttpResponse<String> editOwner = this.browser.get("/owners/" + ownerId + "/edit");
		HttpResponse<String> ownerUpdated = this.browser.post("/owners/" + ownerId + "/edit", editOwner,
				Map.of("firstName", "Uma", "lastName", "CaseEight", "address", "8 Contract Lane", "city", "Amsterdam",
						"telephone", "0612345678"));
		assertThat(ownerUpdated.statusCode()).isEqualTo(302);
		assertThat(this.browser.get("/owners/" + ownerId).body()).contains("Amsterdam");

		HttpResponse<String> newPet = this.browser.get("/owners/" + ownerId + "/pets/new");
		HttpResponse<String> petCreated = this.browser.post("/owners/" + ownerId + "/pets/new", newPet,
				Map.of("name", "Comet", "birthDate", "2020-02-03", "type", "cat"));
		assertThat(petCreated.statusCode()).isEqualTo(302);
		int petId = this.jdbc.queryForObject("select id from pets where owner_id = ? and name = 'Comet'", Integer.class,
				ownerId);
		HttpResponse<String> editPet = this.browser.get("/owners/" + ownerId + "/pets/" + petId + "/edit");
		HttpResponse<String> petUpdated = this.browser.post("/owners/" + ownerId + "/pets/" + petId + "/edit", editPet,
				Map.of("name", "Nova", "birthDate", "2020-02-03", "type", "dog"));
		assertThat(petUpdated.statusCode()).isEqualTo(302);
		assertThat(this.browser.get("/owners/" + ownerId).body()).contains("Nova", "2020-02-03", "dog");

		String firstVetPage = this.browser.get("/vets.html?page=1").body();
		String secondVetPage = this.browser.get("/vets.html?page=2").body();
		assertThat(firstVetPage).contains("James Carter", "Helen Leary", "radiology", "Linda Douglas", "dentistry",
				"surgery", "Rafael Ortega", "Henry Stevens");
		assertThat(secondVetPage).contains("Sharon Jenkins", "none");
	}

	@Test
	void uc8Extensions1a3a3bAndG2RejectInvalidOrSameOwnerDuplicateWithoutMutation() throws Exception {
		this.browser.login("staff", "staff123");
		DatabaseSnapshot before = snapshot();

		HttpResponse<String> noMatch = this.browser.get("/owners?lastName=NoSuchOwner");
		assertThat(noMatch.statusCode()).isEqualTo(200);
		assertThat(noMatch.body()).contains("has not been found").doesNotContain("George Franklin", "Betty Davis");
		assertThat(snapshot()).isEqualTo(before);

		HttpResponse<String> newOwner = this.browser.get("/owners/new");
		HttpResponse<String> invalidOwner = this.browser.post("/owners/new", newOwner,
				Map.of("firstName", "Invalid", "lastName", "Owner", "address", "", "city", "", "telephone", "x"));
		assertThat(invalidOwner.statusCode()).isEqualTo(200);
		assertThat(snapshot()).isEqualTo(before);

		HttpResponse<String> newPet = this.browser.get("/owners/1/pets/new");
		HttpResponse<String> invalidPet = this.browser.post("/owners/1/pets/new", newPet,
				Map.of("name", " ", "birthDate", "2035-01-01"));
		assertThat(invalidPet.statusCode()).isEqualTo(200);
		assertThat(snapshot()).isEqualTo(before);

		HttpResponse<String> duplicate = this.browser.post("/owners/1/pets/new", newPet,
				Map.of("name", "lEO", "birthDate", "2020-01-01", "type", "cat"));
		assertThat(duplicate.statusCode()).isEqualTo(200);
		assertThat(duplicate.body()).contains("is already in use");
		assertThat(snapshot()).isEqualTo(before);

		HttpResponse<String> otherOwnerPet = this.browser.get("/owners/2/pets/new");
		HttpResponse<String> reusedName = this.browser.post("/owners/2/pets/new", otherOwnerPet,
				Map.of("name", "LEO", "birthDate", "2020-01-01", "type", "cat"));
		assertThat(reusedName.statusCode()).isEqualTo(302);
		assertThat(this.jdbc.queryForObject("select count(*) from pets where owner_id = 1 and lower(name) = 'leo'",
				Integer.class))
			.isOne();
		assertThat(this.jdbc.queryForObject("select count(*) from pets where owner_id = 2 and lower(name) = 'leo'",
				Integer.class))
			.isOne();
	}

	@Test
	void uc8Extensions2a2bKeepWalkInsUnlinkedAndScheduledCompletionLinkedToItsDate() throws Exception {
		this.browser.login("staff", "staff123");

		HttpResponse<String> visitForm = this.browser.get("/owners/1/pets/1/visits/new");
		HttpResponse<String> walkIn = this.browser.post("/owners/1/pets/1/visits/new", visitForm,
				Map.of("date", "2030-01-02", "description", "Walk-in wellness check"));
		assertThat(walkIn.statusCode()).isEqualTo(302);
		assertThat(this.jdbc.queryForMap(
				"select pet_id, visit_date, description, appointment_id from visits where description = ?",
				"Walk-in wellness check"))
			.containsEntry("PET_ID", 1)
			.containsEntry("VISIT_DATE", Date.valueOf(LocalDate.of(2030, 1, 2)))
			.containsEntry("DESCRIPTION", "Walk-in wellness check")
			.containsEntry("APPOINTMENT_ID", null);
		assertThat(this.browser.get("/owners/1").body()).contains("2030-01-02", "Walk-in wellness check");

		int appointmentId = insertPastConfirmedAppointment();
		HttpResponse<String> appointment = this.browser.get("/staff/appointments/" + appointmentId);
		HttpResponse<String> completion = this.browser.post("/staff/appointments/" + appointmentId + "/complete",
				appointment, Map.of("description", "Scheduled dental follow-up"));
		assertThat(completion.statusCode()).isEqualTo(302);
		assertThat(this.jdbc.queryForMap(
				"select pet_id, visit_date, description, appointment_id from visits where appointment_id = ?",
				appointmentId))
			.containsEntry("PET_ID", 1)
			.containsEntry("VISIT_DATE", Date.valueOf(LocalDate.of(2026, 9, 4)))
			.containsEntry("DESCRIPTION", "Scheduled dental follow-up")
			.containsEntry("APPOINTMENT_ID", appointmentId);
		assertThat(
				this.jdbc.queryForObject("select status from appointments where id = ?", String.class, appointmentId))
			.isEqualTo("COMPLETED");
	}

	@Test
	void uc8G4OwnerCannotReadOrMutateClinicRecordsThroughRealHttp() throws Exception {
		this.browser.login("george", "george123");
		DatabaseSnapshot before = snapshot();
		HttpResponse<String> ownerPage = this.browser.get("/my/pets");

		assertThat(this.browser.get("/owners/find").statusCode()).isEqualTo(403);
		assertThat(this.browser.get("/owners/new").statusCode()).isEqualTo(403);
		assertThat(this.browser.get("/vets.html").statusCode()).isEqualTo(403);
		assertThat(this.browser.get("/owners/1/pets/1/visits/new").statusCode()).isEqualTo(403);
		assertThat(
				this.browser
					.post("/owners/new", ownerPage,
							Map.of("firstName", "Forged", "lastName", "Owner", "address", "No", "city", "No",
									"telephone", "0000000000"))
					.statusCode())
			.isEqualTo(403);
		assertThat(this.browser
			.post("/owners/1/pets/1/visits/new", ownerPage, Map.of("date", "2030-01-02", "description", "Forged visit"))
			.statusCode()).isEqualTo(403);
		assertThat(snapshot()).isEqualTo(before);
	}

	private int idFromRedirect(HttpResponse<String> response) {
		String location = response.headers().firstValue("Location").orElseThrow();
		String path = URI.create(location).getPath();
		return Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
	}

	private int insertPastConfirmedAppointment() {
		this.jdbc.update("""
				insert into appointments
				(pet_id, vet_id, appointment_date, start_time, end_time, status,
				 last_change_reason, last_changed_by, created_date, created_time)
				values (1, 3, ?, ?, ?, 'CONFIRMED', 'Scheduled care', 'staff', ?, ?)
				""", Date.valueOf(LocalDate.of(2026, 9, 4)), Time.valueOf(LocalTime.of(10, 0)),
				Time.valueOf(LocalTime.of(10, 30)), Date.valueOf(LocalDate.of(2026, 9, 1)),
				Time.valueOf(LocalTime.of(9, 0)));
		return this.jdbc.queryForObject("select max(id) from appointments", Integer.class);
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(this.jdbc.queryForList("select * from owners order by id"),
				this.jdbc.queryForList("select * from pets order by id"),
				this.jdbc.queryForList("select * from visits order by id"),
				this.jdbc.queryForList("select * from appointments order by id"));
	}

	private record DatabaseSnapshot(List<Map<String, Object>> owners, List<Map<String, Object>> pets,
			List<Map<String, Object>> visits, List<Map<String, Object>> appointments) {
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
			String body = submitted.entrySet()
				.stream()
				.map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
				.collect(java.util.stream.Collectors.joining("&"));
			HttpRequest request = HttpRequest.newBuilder(this.baseUri.resolve(path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
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
