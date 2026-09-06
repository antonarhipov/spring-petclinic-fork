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

package org.springframework.samples.petclinic.security;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole-surface denial tests with no-disclosure and no-mutation evidence. */
@SpringBootTest
@Transactional
class SecurityMatrixWebTests {

	enum RouteScope {

		PUBLIC, AUTHENTICATED, STAFF, OWNER

	}

	record FinalRoute(String method, String pattern, String testPath, RouteScope scope) {
	}

	private static final List<FinalRoute> FINAL_ROUTES = List.of(
			// Public routes
			new FinalRoute("GET", "/login", "/login", RouteScope.PUBLIC),
			new FinalRoute("POST", "/login", "/login", RouteScope.PUBLIC),
			new FinalRoute("GET", "/error", "/error", RouteScope.PUBLIC),

			// Authenticated routes
			new FinalRoute("GET", "/", "/", RouteScope.AUTHENTICATED),
			new FinalRoute("GET", "/403", "/403", RouteScope.AUTHENTICATED),
			new FinalRoute("POST", "/logout", "/logout", RouteScope.AUTHENTICATED),

			// Staff-only routes
			new FinalRoute("GET", "/oups", "/oups", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/new", "/owners/new", RouteScope.STAFF),
			new FinalRoute("POST", "/owners/new", "/owners/new", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/find", "/owners/find", RouteScope.STAFF),
			new FinalRoute("GET", "/owners", "/owners", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/{ownerId}", "/owners/1", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/{ownerId}/edit", "/owners/1/edit", RouteScope.STAFF),
			new FinalRoute("POST", "/owners/{ownerId}/edit", "/owners/1/edit", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/{ownerId}/pets/new", "/owners/1/pets/new", RouteScope.STAFF),
			new FinalRoute("POST", "/owners/{ownerId}/pets/new", "/owners/1/pets/new", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/{ownerId}/pets/{petId}/edit", "/owners/1/pets/1/edit", RouteScope.STAFF),
			new FinalRoute("POST", "/owners/{ownerId}/pets/{petId}/edit", "/owners/1/pets/1/edit", RouteScope.STAFF),
			new FinalRoute("GET", "/owners/{ownerId}/pets/{petId}/visits/new", "/owners/1/pets/1/visits/new",
					RouteScope.STAFF),
			new FinalRoute("POST", "/owners/{ownerId}/pets/{petId}/visits/new", "/owners/1/pets/1/visits/new",
					RouteScope.STAFF),
			new FinalRoute("GET", "/vets", "/vets", RouteScope.STAFF),
			new FinalRoute("GET", "/vets.html", "/vets.html", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/queue", "/staff/queue", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/requests/{requestId}", "/staff/requests/999999", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/requests/{requestId}/interpretation", "/staff/requests/999999/interpretation",
					RouteScope.STAFF),
			new FinalRoute("POST", "/staff/requests/{requestId}/interpretation",
					"/staff/requests/999999/interpretation", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/requests/{requestId}/suggest", "/staff/requests/999999/suggest",
					RouteScope.STAFF),
			new FinalRoute("POST", "/staff/requests/{requestId}/release-hold", "/staff/requests/999999/release-hold",
					RouteScope.STAFF),
			new FinalRoute("GET", "/staff/appointments/new", "/staff/appointments/new", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/appointments", "/staff/appointments", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/calendar", "/staff/calendar", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/calendar/pick/{requestId}", "/staff/calendar/pick/999999", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/calendar/pick/{requestId}", "/staff/calendar/pick/999999", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/appointments/{appointmentId}/reschedule",
					"/staff/appointments/999999/reschedule", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/appointments/{appointmentId}/reschedule",
					"/staff/appointments/999999/reschedule", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/appointments/{appointmentId}/cancel", "/staff/appointments/999999/cancel",
					RouteScope.STAFF),
			new FinalRoute("POST", "/staff/appointments/{appointmentId}/complete",
					"/staff/appointments/999999/complete", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/appointments/{appointmentId}/no-show", "/staff/appointments/999999/no-show",
					RouteScope.STAFF),
			new FinalRoute("GET", "/staff/visits/{visitId}/edit", "/staff/visits/999999/edit", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/visits/{visitId}/edit", "/staff/visits/999999/edit", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/settings", "/staff/settings", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/settings", "/staff/settings", RouteScope.STAFF),
			new FinalRoute("GET", "/staff/vets/{vetId}/availability", "/staff/vets/1/availability", RouteScope.STAFF),
			new FinalRoute("POST", "/staff/vets/{vetId}/availability", "/staff/vets/1/availability", RouteScope.STAFF),

			// Owner-only routes
			new FinalRoute("GET", "/my/pets", "/my/pets", RouteScope.OWNER),
			new FinalRoute("GET", "/my/appointments", "/my/appointments", RouteScope.OWNER),
			new FinalRoute("GET", "/my/appointments/{appointmentId}", "/my/appointments/999999", RouteScope.OWNER),
			new FinalRoute("POST", "/my/appointments/{appointmentId}/cancel", "/my/appointments/999999/cancel",
					RouteScope.OWNER),
			new FinalRoute("GET", "/my/requests/new", "/my/requests/new", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests", "/my/requests", RouteScope.OWNER),
			new FinalRoute("GET", "/my/requests/{requestId}", "/my/requests/999999", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/consent", "/my/requests/999999/consent", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/decline", "/my/requests/999999/decline", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/confirm", "/my/requests/999999/confirm", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/accept", "/my/requests/999999/accept", RouteScope.OWNER),
			new FinalRoute("GET", "/my/requests/{requestId}/edit", "/my/requests/999999/edit", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/edit", "/my/requests/999999/edit", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/abandon", "/my/requests/999999/abandon", RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/route-to-staff", "/my/requests/999999/route-to-staff",
					RouteScope.OWNER),
			new FinalRoute("POST", "/my/requests/{requestId}/another", "/my/requests/999999/another",
					RouteScope.OWNER));

	private static final List<String> MUTABLE_TABLES = List.of("owners", "pets", "visits", "scheduling_request",
			"scheduling_request_event", "interpretation", "interpretation_window", "appointment", "appointment_change");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-1")
	void finalRouteTableCoversEveryMappedHandler() {
		Set<String> mappedHandlers = new LinkedHashSet<>();
		for (RequestMappingInfo info : this.handlerMapping.getHandlerMethods().keySet()) {
			Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
			for (String pattern : info.getPatternValues()) {
				if (methods.isEmpty()) {
					mappedHandlers.add("GET " + pattern);
				}
				else {
					methods.forEach(method -> mappedHandlers.add(method.name() + " " + pattern));
				}
			}
		}
		mappedHandlers.add("POST /login");
		mappedHandlers.add("POST /logout");

		Set<String> tableRoutes = FINAL_ROUTES.stream()
			.map(r -> r.method() + " " + r.pattern())
			.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<String> extraInTable = new TreeSet<>(tableRoutes);
		extraInTable.removeAll(mappedHandlers);
		Set<String> missingInTable = new TreeSet<>(mappedHandlers);
		missingInTable.removeAll(tableRoutes);

		assertThat(extraInTable).as("Extra routes in table: " + extraInTable).isEmpty();
		assertThat(missingInTable).as("Missing routes in table: " + missingInTable).isEmpty();
		assertThat(tableRoutes).as("Final route table must cover every mapped handler").isEqualTo(mappedHandlers);
	}

	@Test
	@Tag("AC-1")
	@Tag("AC-2")
	@Tag("AC-8")
	@Tag("AC-9")
	@Tag("AC-10")
	void everyFinalRouteDeniedForAnonymousWrongRoleAndOtherOwner() throws Exception {
		Map<String, List<Map<String, Object>>> before = databaseSnapshot();

		MockHttpSession ownerSession = login("george", "george123");
		MockHttpSession staffSession = login("staff", "staff123");
		MockHttpSession otherOwnerSession = login("betty", "betty123");

		for (FinalRoute route : FINAL_ROUTES) {
			// 1. Anonymous principal: protected routes must 302 -> /login
			if (route.scope() != RouteScope.PUBLIC) {
				MockHttpServletRequestBuilder req = "GET".equals(route.method()) ? get(route.testPath())
						: post(route.testPath()).with(csrf());
				assertAnonymousDenied(req, route.testPath());
			}

			// 2. Wrong-role principal:
			if (route.scope() == RouteScope.STAFF) {
				MockHttpServletRequestBuilder req = "GET".equals(route.method())
						? get(route.testPath()).session(ownerSession)
						: post(route.testPath()).session(ownerSession).with(csrf());
				assertWrongRoleDenied(req, route.testPath());
			}
			else if (route.scope() == RouteScope.OWNER) {
				MockHttpServletRequestBuilder req = "GET".equals(route.method())
						? get(route.testPath()).session(staffSession)
						: post(route.testPath()).session(staffSession).with(csrf());
				assertWrongRoleDenied(req, route.testPath());
			}

			// 3. Other-owner principal on owner-scoped parameterized routes: must return
			// identical 404 with no disclosure
			if (route.scope() == RouteScope.OWNER && route.pattern().contains("{")) {
				MockHttpServletRequestBuilder otherReq = "GET".equals(route.method())
						? get(route.testPath()).session(otherOwnerSession)
						: post(route.testPath()).session(otherOwnerSession).with(csrf());
				this.mockMvc.perform(otherReq)
					.andExpect(status().isNotFound())
					.andExpect(content().string(noProtectedData()));
			}
		}

		assertThat(databaseSnapshot()).as("Whole security surface denial must produce 0 mutations").isEqualTo(before);
	}

	@Test
	@Tag("AC-11")
	@Tag("AC-12")
	void otherOwnerAndMissingRequestAreIdenticalAndCannotBeMutated() throws Exception {
		Owner betty = this.ownerRepository.findById(2).orElseThrow();
		Pet bettysPet = betty.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest bettysRequest = this.lifecycleService.createRequest(betty, bettysPet, "Protected reason text",
				"Protected availability text", "betty");
		MockHttpSession george = login("george", "george123");
		long eventsBefore = this.eventRepository.count();

		MvcResult otherOwner = this.mockMvc.perform(get("/my/requests/{id}", bettysRequest.getId()).session(george))
			.andExpect(status().isNotFound())
			.andExpect(content().string(allOf(not(containsString("Protected reason text")),
					not(containsString("Protected availability text")))))
			.andReturn();
		MvcResult missing = this.mockMvc.perform(get("/my/requests/{id}", 999999).session(george))
			.andExpect(status().isNotFound())
			.andReturn();
		assertThat(otherOwner.getResponse().getContentAsString()).isEqualTo(missing.getResponse().getContentAsString());

		this.mockMvc.perform(post("/my/requests/{id}/consent", bettysRequest.getId()).session(george).with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(content().string(allOf(not(containsString("Protected reason text")),
					not(containsString("Protected availability text")))));
		SchedulingRequest unchanged = this.requestRepository.findById(bettysRequest.getId()).orElseThrow();
		assertThat(unchanged.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(this.eventRepository.count()).isEqualTo(eventsBefore);
	}

	private void assertAnonymousDenied(MockHttpServletRequestBuilder request, String route) throws Exception {
		this.mockMvc.perform(request)
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", endsWith("/login")))
			.andExpect(content().string(noProtectedData()));
	}

	private void assertWrongRoleDenied(MockHttpServletRequestBuilder request, String route) throws Exception {
		this.mockMvc.perform(request).andExpect(status().isForbidden()).andExpect(content().string(noProtectedData()));
	}

	private static org.hamcrest.Matcher<String> noProtectedData() {
		return allOf(not(containsString("George")), not(containsString("Franklin")), not(containsString("James")),
				not(containsString("Carter")), not(containsString("Leo")));
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private Map<String, List<Map<String, Object>>> databaseSnapshot() {
		Map<String, List<Map<String, Object>>> snapshot = new LinkedHashMap<>();
		for (String table : MUTABLE_TABLES) {
			snapshot.put(table, this.jdbcTemplate.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
		}
		return snapshot;
	}

}
