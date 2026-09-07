package org.springframework.samples.petclinic.scheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresentationShellTests {

	private static final Path TEMPLATES = Path.of("src/main/resources/templates");

	@Autowired
	private MockMvc mvc;

	@Test
	void uc1MainLoginUsesTheEstablishedPetClinicLayout() throws Exception {
		String html = rendered(get("/login"));
		assertThat(html).contains("<nav", "navbar", "<main", "xd-container", "spring-logo.svg", "</body>");
		assertThat(occurrences(html, "rel=\"stylesheet\"")).isOne();
		assertThat(html).contains("/resources/css/petclinic.css").doesNotContain("<style", "style=");
	}

	@Test
	void uc1MainAuthenticatedLandingsShowIdentityLogoutAndExactRoleMenus() throws Exception {
		String owner = authenticated(get("/my/appointments").with(user("george").roles("OWNER")), "george");
		assertThat(occurrences(owner, "<li class=\"nav-item\">")).isEqualTo(2);
		assertThat(owner).contains(">My pets</a>", ">My appointments</a>")
			.doesNotContain(">Home</a>", ">Find Owners</a>", ">Veterinarians</a>", ">Scheduling queue</a>",
					">Calendar</a>", ">Clinic settings</a>", ">Error</a>");

		String staff = authenticated(get("/staff/queue").with(user("staff").roles("STAFF")), "staff");
		assertThat(occurrences(staff, "<li class=\"nav-item\">")).isEqualTo(7);
		assertThat(staff)
			.contains(">Home</a>", ">Find Owners</a>", ">Veterinarians</a>", ">Scheduling queue</a>", ">Calendar</a>",
					">Clinic settings</a>", ">Error</a>")
			.doesNotContain(">My pets</a>", ">My appointments</a>");
	}

	@Test
	void uc1GuaranteeAllUc1PagesUseTheSingleSharedLayoutWithoutInlineStyles() throws Exception {
		List<Path> layouts;
		try (var paths = Files.walk(TEMPLATES)) {
			layouts = paths.filter(path -> path.getFileName().toString().contains("layout")).toList();
		}
		assertThat(layouts).containsExactly(TEMPLATES.resolve("fragments/layout.html"));
		for (Path template : List.of(TEMPLATES.resolve("login.html"), TEMPLATES.resolve("my/pets.html"),
				TEMPLATES.resolve("my/appointments.html"), TEMPLATES.resolve("staff/queue.html"))) {
			assertThat(Files.readString(template)).contains("fragments/layout :: layout")
				.doesNotContain("<style", "style=");
		}
		String layout = Files.readString(TEMPLATES.resolve("fragments/layout.html"));
		assertThat(occurrences(layout, "<link rel=\"stylesheet\"")).isOne();
		assertThat(layout).contains("@{/resources/css/petclinic.css}").doesNotContain("<style", "style=");
	}

	private String authenticated(RequestBuilder request, String username) throws Exception {
		String html = rendered(request);
		assertThat(html).contains(">" + username + "</span>", "action=\"/logout\"", "name=\"_csrf\"",
				">Logout</button>");
		return html;
	}

	private String rendered(RequestBuilder request) throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private int occurrences(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}

}
