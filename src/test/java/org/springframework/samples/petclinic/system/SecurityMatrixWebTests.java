package org.springframework.samples.petclinic.system;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PetClinicApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityMatrixWebTests {

	private static final List<Route> OWNER_ROUTES = List.of(route("/my/pets"), route("/my/appointments"),
			route("/my/appointments/1"), route("/my/appointments/1/cancel"), route("/my/requests/new"),
			route("/my/requests/1"), route("/my/requests/1/edit"), route("/my/requests/1/consent"),
			route("/my/requests/1/decline"), route("/my/requests/1/confirm"), route("/my/requests/1/another-option"),
			route("/my/requests/1/accept"), route("/my/requests/1/staff-assistance"), route("/my/requests/1/abandon"),
			route("/my/requests/1/status"));

	private static final List<Route> STAFF_ROUTES = List.of(route("/owners/new"), route("/owners/find"),
			route("/owners"), route("/owners/1"), route("/owners/1/edit"), route("/owners/1/pets/new"),
			route("/owners/1/pets/1/edit"), route("/owners/1/pets/1/visits/new"), route("/vets"), route("/vets.html"),
			route("/staff/queue"), route("/staff/requests"), route("/staff/requests/1"),
			route("/staff/requests/1/interpretation"), route("/staff/requests/1/release-hold"),
			route("/staff/requests/1/suggest"), route("/staff/requests/1/book"), route("/staff/calendar"),
			route("/staff/calendar/book"), route("/staff/settings"), route("/staff/appointments/1"),
			route("/staff/appointments/1/reschedule"), route("/staff/appointments/1/cancel"),
			route("/staff/appointments/1/complete"), route("/staff/appointments/1/no-show"), route("/actuator"),
			route("/actuator/env"), route("/h2-console"), route("/oups"));

	private static final List<Route> AUTHENTICATED_ROUTES = List.of(route("/"), route("/error"),
			route("/definitely-not-a-route"));

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	@Tag("AC-4")
	void ac4_anonymous_every_protected_route_redirects_to_login() throws Exception {
		for (Route route : allProtectedRoutes()) {
			for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
				this.mvc.perform(request(method, route.path()))
					.andExpect(status().isFound())
					.andExpect(redirectedUrl("/login"));
			}
		}
	}

	@Test
	@Tag("AC-5")
	void ac5_existing_and_unknown_redirects_are_identical() throws Exception {
		var existing = this.mvc.perform(request(HttpMethod.GET, "/owners/1")).andReturn().getResponse();
		var unknown = this.mvc.perform(request(HttpMethod.GET, "/not-present-anywhere")).andReturn().getResponse();
		assertThat(existing.getStatus()).isEqualTo(unknown.getStatus()).isEqualTo(302);
		assertThat(existing.getHeader("Location")).isEqualTo(unknown.getHeader("Location")).endsWith("/login");
		assertThat(existing.getContentAsByteArray()).isEqualTo(unknown.getContentAsByteArray()).isEmpty();
	}

	@Test
	@Tag("AC-12")
	void ac12_owner_every_staff_route_is_403_without_data_or_mutation() throws Exception {
		DatabaseSnapshot before = snapshot();
		for (Route route : STAFF_ROUTES) {
			for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
				this.mvc.perform(authorized(method, route.path(), owner()))
					.andExpect(status().isForbidden())
					.andExpect(
							content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("George"))))
					.andExpect(content()
						.string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Surgery"))));
			}
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@Tag("AC-13")
	void ac13_denied_posts_never_invoke_mutators() throws Exception {
		DatabaseSnapshot before = snapshot();
		for (Route route : allProtectedRoutes()) {
			this.mvc.perform(request(HttpMethod.POST, route.path()))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "/login"));
		}
		for (Route route : STAFF_ROUTES) {
			this.mvc.perform(authorized(HttpMethod.POST, route.path(), owner())).andExpect(status().isForbidden());
		}
		for (Route route : OWNER_ROUTES) {
			this.mvc.perform(authorized(HttpMethod.POST, route.path(), staff())).andExpect(status().isForbidden());
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	@Tag("AC-14")
	void ac14_staff_every_my_route_is_403_without_owner_data() throws Exception {
		DatabaseSnapshot before = snapshot();
		for (Route route : OWNER_ROUTES) {
			for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
				this.mvc.perform(authorized(method, route.path(), staff()))
					.andExpect(status().isForbidden())
					.andExpect(
							content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("George"))))
					.andExpect(
							content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Max"))));
			}
		}
		assertThat(snapshot()).isEqualTo(before);
	}

	private MockHttpServletRequestBuilder authorized(HttpMethod method, String path, RequestPostProcessor actor) {
		return request(method, path).with(actor).with(csrf());
	}

	private RequestPostProcessor owner() {
		return user("george").roles("OWNER");
	}

	private RequestPostProcessor staff() {
		return user("staff").roles("STAFF");
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(rows("owners"), rows("pets"), rows("visits"), rows("scheduling_requests"),
				rows("appointments"), rows("clinic_settings"), rows("clinic_opening_hours"), rows("vet_working_blocks"),
				rows("vet_exceptions"), rows("vet_leave"), rows("clinic_closures"));
	}

	private List<Map<String, Object>> rows(String table) {
		return this.jdbc.queryForList("select * from " + table + " order by id");
	}

	private static List<Route> allProtectedRoutes() {
		return java.util.stream.Stream.of(AUTHENTICATED_ROUTES, OWNER_ROUTES, STAFF_ROUTES)
			.flatMap(List::stream)
			.toList();
	}

	private static Route route(String path) {
		return new Route(path);
	}

	private record Route(String path) {
	}

	private record DatabaseSnapshot(List<Map<String, Object>> owners, List<Map<String, Object>> pets,
			List<Map<String, Object>> visits, List<Map<String, Object>> requests,
			List<Map<String, Object>> appointments, List<Map<String, Object>> settings,
			List<Map<String, Object>> openingHours, List<Map<String, Object>> workingBlocks,
			List<Map<String, Object>> exceptions, List<Map<String, Object>> leave, List<Map<String, Object>> closures) {
	}

}
