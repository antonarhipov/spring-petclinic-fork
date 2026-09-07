package org.springframework.samples.petclinic.system;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PetClinicApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationWebTests {

	@Autowired
	private MockMvc mvc;

	@Test
	@Tag("AC-1")
	void ac1_all_seeded_credentials_authenticate_with_role() throws Exception {
		Map<String, String> credentials = new LinkedHashMap<>();
		for (String owner : new String[] { "george", "betty", "eduardo", "harold", "peter", "jean", "jeff", "maria",
				"david", "carlos" }) {
			credentials.put(owner, "OWNER");
		}
		credentials.put("admin", "STAFF");
		credentials.put("staff", "STAFF");

		for (Map.Entry<String, String> credential : credentials.entrySet()) {
			String username = credential.getKey();
			String role = credential.getValue();
			var result = this.mvc
				.perform(post("/login").with(csrf()).param("username", username).param("password", username + "123"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(role.equals("OWNER") ? "/my/appointments" : "/staff/queue"))
				.andReturn();
			SecurityContext context = (SecurityContext) result.getRequest()
				.getSession(false)
				.getAttribute("SPRING_SECURITY_CONTEXT");
			assertThat(context.getAuthentication().getName()).isEqualTo(username);
			assertThat(context.getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority)
				.filteredOn(authority -> authority.startsWith("ROLE_"))
				.containsExactly("ROLE_" + role);
		}
	}

	@Test
	@Tag("AC-3")
	void ac3_wrong_credentials_are_neutral_and_localized() throws Exception {
		String unknown = failedLoginBody("missing-user", "wrong-password");
		String wrongPassword = failedLoginBody("george", "wrong-password");
		assertThat(withoutCsrfValue(unknown)).isEqualTo(withoutCsrfValue(wrongPassword));
		assertThat(unknown).contains("Invalid username or password")
			.doesNotContain("missing-user", "george", "wrong-password", "username was", "password was");
	}

	@Test
	@Tag("AC-6")
	void ac6_health_is_anonymous() throws Exception {
		this.mvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("application/vnd.spring-boot.actuator.v3+json"));
		this.mvc.perform(get("/actuator")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
		this.mvc.perform(get("/actuator/env")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@Tag("AC-7")
	void ac7_login_and_root_land_by_role() throws Exception {
		assertLoginLanding("george", "/my/appointments");
		assertLoginLanding("staff", "/staff/queue");
		this.mvc.perform(get("/").with(user("george").roles("OWNER")))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/my/appointments"));
		this.mvc.perform(get("/").with(user("staff").roles("STAFF")))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/staff/queue"));
	}

	@Test
	@Tag("AC-9")
	void ac9_logout_invalidates_session() throws Exception {
		var login = this.mvc
			.perform(post("/login").with(csrf()).param("username", "george").param("password", "george123"))
			.andReturn();
		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		String sessionId = session.getId();
		this.mvc.perform(post("/logout").session(session).with(csrf()))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login"));
		assertThat(session.isInvalid()).isTrue();
		this.mvc.perform(get("/my/appointments").cookie(new Cookie("JSESSIONID", sessionId)))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login"));
	}

	private String failedLoginBody(String username, String password) throws Exception {
		var failed = this.mvc
			.perform(post("/login").with(csrf()).param("username", username).param("password", password))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login?error"))
			.andReturn();
		return this.mvc.perform(get(failed.getResponse().getRedirectedUrl()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private void assertLoginLanding(String username, String landing) throws Exception {
		this.mvc.perform(post("/login").with(csrf()).param("username", username).param("password", username + "123"))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl(landing));
	}

	private String withoutCsrfValue(String body) {
		return body.replaceAll("(name=\"_csrf\" value=\")[^\"]+", "$1<token>");
	}

}
