/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class StaffManagementRouteTests {

	private static final List<String> MUTABLE_TABLES = List.of("owners", "pets", "visits", "scheduling_request",
			"scheduling_request_event", "interpretation", "interpretation_window", "appointment", "appointment_change");

	private static final List<RouteSpec> MANAGEMENT_ROUTES = List.of(new RouteSpec("GET", "/staff/calendar", true),
			new RouteSpec("GET", "/staff/settings", true), new RouteSpec("POST", "/staff/settings", false),
			new RouteSpec("GET", "/staff/vets/1/availability", true),
			new RouteSpec("POST", "/staff/vets/1/availability", false),
			new RouteSpec("GET", "/staff/appointments/1/reschedule", true),
			new RouteSpec("POST", "/staff/appointments/1/reschedule", false),
			new RouteSpec("POST", "/staff/appointments/1/cancel", false),
			new RouteSpec("POST", "/staff/appointments/1/complete", false),
			new RouteSpec("POST", "/staff/appointments/1/no-show", false),
			new RouteSpec("GET", "/staff/visits/1/edit", true), new RouteSpec("POST", "/staff/visits/1/edit", false));

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-96")
	@WithMockUser(username = "staff", roles = "STAFF")
	void allManagementRoutesResolveForStaff() throws Exception {
		Map<RequestMappingInfo, HandlerMethod> handlerMethods = this.handlerMapping.getHandlerMethods();

		int calendarHandlerCount = 0;
		int settingsHandlerCount = 0;

		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
			Set<String> patterns = entry.getKey().getPatternValues();
			Set<org.springframework.web.bind.annotation.RequestMethod> methods = entry.getKey()
				.getMethodsCondition()
				.getMethods();

			if (patterns.contains("/staff/calendar") && (methods.isEmpty()
					|| methods.contains(org.springframework.web.bind.annotation.RequestMethod.GET))) {
				calendarHandlerCount++;
			}
			if (patterns.contains("/staff/settings") && (methods.isEmpty()
					|| methods.contains(org.springframework.web.bind.annotation.RequestMethod.GET))) {
				settingsHandlerCount++;
			}
		}

		assertThat(calendarHandlerCount).as("GET /staff/calendar must resolve to exactly one handler").isOne();
		assertThat(settingsHandlerCount).as("GET /staff/settings must resolve to exactly one handler").isOne();

		Path obsoletePath = Path
			.of("src/main/java/org/springframework/samples/petclinic/scheduling/web/StaffPageController.java");
		assertThat(Files.exists(obsoletePath)).as("StaffPageController.java must be deleted").isFalse();

		for (RouteSpec route : MANAGEMENT_ROUTES) {
			MockHttpServletRequestBuilder requestBuilder = route.isGet() ? get(route.path())
					: post(route.path()).with(csrf());
			MvcResult result = this.mockMvc.perform(requestBuilder).andReturn();
			int status = result.getResponse().getStatus();
			assertThat(status).as("Route %s %s must resolve for staff without 404 or 403", route.method(), route.path())
				.isNotIn(403, 404, 500);
		}
	}

	@Test
	@Tag("AC-9")
	void ownerAndAnonymousDeniedWithoutDisclosureOrMutation() throws Exception {
		Map<String, Integer> beforeCounts = captureTableCounts();

		// Anonymous requests must be redirected to login (302)
		for (RouteSpec route : MANAGEMENT_ROUTES) {
			MockHttpServletRequestBuilder req = route.isGet() ? get(route.path()) : post(route.path()).with(csrf());
			this.mockMvc.perform(req)
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/login")));
		}

		// Owner requests must be forbidden (403)
		MockHttpSession ownerSession = loginOwner();
		for (RouteSpec route : MANAGEMENT_ROUTES) {
			MockHttpServletRequestBuilder req = route.isGet() ? get(route.path()).session(ownerSession)
					: post(route.path()).session(ownerSession).with(csrf());
			MvcResult result = this.mockMvc.perform(req).andExpect(status().isForbidden()).andReturn();
			String body = result.getResponse().getContentAsString();
			assertThat(body).doesNotContain("Clinic settings saved", "Veterinarian availability",
					"appointmentRescheduled");
		}

		Map<String, Integer> afterCounts = captureTableCounts();
		assertThat(afterCounts).as("Denials must produce no mutations").isEqualTo(beforeCounts);
	}

	private MockHttpSession loginOwner() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private Map<String, Integer> captureTableCounts() {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String table : MUTABLE_TABLES) {
			Integer count = this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
			counts.put(table, count != null ? count : 0);
		}
		return counts;
	}

	private record RouteSpec(String method, String path, boolean isGet) {
	}

}
