package org.springframework.samples.petclinic.system;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PetClinicApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT,
		properties = "spring.h2.console.enabled=true")
@ActiveProfiles("test")
class AuthenticationE2ETests {

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void uc1MainOwnerAndStaffSignInAndEnterOnlyTheirWorkspace() throws Exception {
		Browser owner = browser();
		assertRedirect(owner.get("/"), "/login");
		HttpResponse<String> loginPage = owner.get("/login");
		assertThat(loginPage.statusCode()).isEqualTo(200);
		assertThat(loginPage.body()).contains("petclinic.css", "Sign in").doesNotContain("George Franklin");

		HttpResponse<String> ownerLogin = owner.login(loginPage, "george", "george123");
		assertRedirect(ownerLogin, "/my/appointments");
		String ownerWorkspace = owner.get("/my/appointments").body();
		assertThat(ownerWorkspace)
			.contains(">george</span>", ">Logout</button>", ">My pets</a>", ">My appointments</a>")
			.doesNotContain(">Home</a>", ">Find Owners</a>", ">Veterinarians</a>", ">Scheduling queue</a>",
					">Calendar</a>", ">Clinic settings</a>", ">Error</a>");

		Browser staff = browser();
		HttpResponse<String> staffLogin = staff.login(staff.get("/login"), "staff", "staff123");
		assertRedirect(staffLogin, "/staff/queue");
		String staffWorkspace = staff.get("/staff/queue").body();
		assertThat(staffWorkspace)
			.contains(">staff</span>", ">Logout</button>", ">Home</a>", ">Find Owners</a>", ">Veterinarians</a>",
					">Scheduling queue</a>", ">Calendar</a>", ">Clinic settings</a>", ">Error</a>")
			.doesNotContain(">My pets</a>", ">My appointments</a>");
	}

	@Test
	void uc1ExtensionsInvalidLogoutAndRoleGuardsPreserveTheBoundary() throws Exception {
		Browser unknown = browser();
		HttpResponse<String> unknownFailure = unknown.login(unknown.get("/login"), "missing", "wrong");
		assertRedirect(unknownFailure, "/login?error");
		String unknownBody = unknown.get("/login?error").body();

		Browser wrongPassword = browser();
		HttpResponse<String> passwordFailure = wrongPassword.login(wrongPassword.get("/login"), "george", "wrong");
		assertRedirect(passwordFailure, "/login?error");
		String passwordBody = wrongPassword.get("/login?error").body();
		assertThat(normalizeCsrf(passwordBody)).isEqualTo(normalizeCsrf(unknownBody));
		assertThat(passwordBody).contains("Invalid username or password").doesNotContain("george", "missing");
		assertRedirect(wrongPassword.get("/my/appointments"), "/login");

		Browser anonymous = browser();
		assertThat(anonymous.get("/actuator/health").statusCode()).isEqualTo(200);
		assertThat(anonymous.get("/resources/css/petclinic.css").statusCode()).isEqualTo(200);
		for (String protectedPath : new String[] { "/my/requests/1/status", "/owners/1", "/staff/queue",
				"/actuator/env", "/h2-console/", "/unknown" }) {
			assertRedirect(anonymous.get(protectedPath), "/login");
		}

		Browser owner = browser();
		owner.login(owner.get("/login"), "george", "george123");
		assertThat(owner.get("/owners/2").statusCode()).isEqualTo(403);
		assertThat(owner.get("/oups").statusCode()).isEqualTo(403);
		HttpResponse<String> ownerPage = owner.get("/my/appointments");
		assertRedirect(owner.post("/logout", ownerPage, Map.of()), "/login");
		assertRedirect(owner.get("/my/appointments"), "/login");

		Browser staff = browser();
		staff.login(staff.get("/login"), "staff", "staff123");
		assertThat(staff.get("/my/pets").statusCode()).isEqualTo(403);
		assertThat(staff.get("/actuator").statusCode()).isEqualTo(200);
		assertThat(staff.get("/h2-console/").statusCode()).isEqualTo(200);
	}

	@Test
	void uc1ExtensionForeignAndUnknownOwnerResourcesHaveTheSame404AndNoMutation() throws Exception {
		Integer bettysPet = this.jdbc.queryForObject("select min(id) from pets where owner_id = 2", Integer.class);
		this.jdbc.update("""
				insert into scheduling_requests
				(pet_id, active_pet_id, request_text, state, failure_count, created_date, created_time, version)
				values (?, null, 'private request', 'ABANDONED', 0, DATE '2026-09-07', TIME '09:00:00', 0)
				""", bettysPet);
		int foreignId = this.jdbc.queryForObject("select max(id) from scheduling_requests", Integer.class);
		int before = this.jdbc.queryForObject("select count(*) from scheduling_requests", Integer.class);

		try {
			Browser george = browser();
			george.login(george.get("/login"), "george", "george123");
			HttpResponse<String> foreign = george.get("/my/requests/" + foreignId + "/status");
			HttpResponse<String> unknown = george.get("/my/requests/" + (foreignId + 10000) + "/status");
			assertThat(foreign.statusCode()).isEqualTo(404);
			assertThat(unknown.statusCode()).isEqualTo(404);
			assertThat(normalizeCsrf(foreign.body())).isEqualTo(normalizeCsrf(unknown.body()));
			assertThat(foreign.body()).doesNotContain("private request", "Betty Davis");
			assertThat(this.jdbc.queryForObject("select count(*) from scheduling_requests", Integer.class))
				.isEqualTo(before);
		}
		finally {
			this.jdbc.update("delete from scheduling_requests where id = ?", foreignId);
		}
	}

	private Browser browser() {
		return new Browser(this.port);
	}

	private void assertRedirect(HttpResponse<String> response, String path) {
		assertThat(response.statusCode()).isEqualTo(302);
		assertThat(response.headers().firstValue("Location"))
			.hasValueSatisfying(location -> assertThat(location).endsWith(path));
	}

	private String normalizeCsrf(String body) {
		return body.replaceAll("(name=\"_csrf\" value=\")[^\"]+", "$1<token>");
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

		private HttpResponse<String> login(HttpResponse<String> loginPage, String username, String password)
				throws Exception {
			return post("/login", loginPage, Map.of("username", username, "password", password));
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
