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
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.context.WebApplicationContext;

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

	private static final List<String> ANONYMOUS_GET_ROUTES = List.of("/", "/403", "/oups", "/owners/new",
			"/owners/find", "/owners", "/owners/1", "/owners/1/edit", "/owners/1/pets/new", "/owners/1/pets/1/edit",
			"/owners/1/pets/1/visits/new", "/vets", "/vets.html", "/staff/queue", "/staff/calendar", "/staff/settings",
			"/my/pets", "/my/appointments", "/my/requests/new", "/my/requests/999999", "/actuator/health");

	private static final List<String> ANONYMOUS_POST_ROUTES = List.of("/logout", "/owners/new", "/owners/1/edit",
			"/owners/1/pets/new", "/owners/1/pets/1/edit", "/owners/1/pets/1/visits/new", "/my/requests",
			"/my/requests/999999/consent", "/my/requests/999999/decline", "/my/requests/999999/confirm",
			"/my/requests/999999/accept");

	private static final List<String> STAFF_GET_ROUTES = List.of("/oups", "/owners/new", "/owners/find", "/owners",
			"/owners/1", "/owners/1/edit", "/owners/1/pets/new", "/owners/1/pets/1/edit", "/owners/1/pets/1/visits/new",
			"/vets", "/vets.html", "/staff/queue", "/staff/calendar", "/staff/settings", "/actuator/health");

	private static final List<String> STAFF_POST_ROUTES = List.of("/owners/new", "/owners/1/edit", "/owners/1/pets/new",
			"/owners/1/pets/1/edit", "/owners/1/pets/1/visits/new");

	private static final List<String> OWNER_GET_ROUTES = List.of("/my/pets", "/my/appointments", "/my/requests/new",
			"/my/requests/999999");

	private static final List<String> OWNER_POST_ROUTES = List.of("/my/requests", "/my/requests/999999/consent",
			"/my/requests/999999/decline", "/my/requests/999999/confirm", "/my/requests/999999/accept");

	private static final List<String> MUTABLE_TABLES = List.of("owners", "pets", "visits", "scheduling_request",
			"scheduling_request_event", "interpretation", "interpretation_window", "appointment", "appointment_change");

	@Autowired
	private WebApplicationContext context;

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
	@org.junit.jupiter.api.DisplayName("AC-1 AC-2: anonymous protected surface redirects without disclosure or mutation")
	void anonymousProtectedSurfaceRedirectsWithoutDisclosureOrMutation() throws Exception {
		Map<String, List<Map<String, Object>>> before = databaseSnapshot();
		for (String route : ANONYMOUS_GET_ROUTES) {
			assertAnonymousDenied(get(route), route);
		}
		for (String route : ANONYMOUS_POST_ROUTES) {
			assertAnonymousDenied(post(route).with(csrf()), route);
		}
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-8 AC-9: owner cannot reach staff surface or mutate staff data")
	void ownerCannotReachAnyStaffSurfaceOrMutateStaffData() throws Exception {
		MockHttpSession owner = login("george", "george123");
		Map<String, List<Map<String, Object>>> before = databaseSnapshot();
		for (String route : STAFF_GET_ROUTES) {
			assertWrongRoleDenied(get(route).session(owner), route);
		}
		for (String route : STAFF_POST_ROUTES) {
			assertWrongRoleDenied(post(route).session(owner).with(csrf()), route);
		}
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	void staffCannotReachOwnerSurfaceOrMutateOwnerData() throws Exception {
		MockHttpSession staff = login("staff", "staff123");
		Map<String, List<Map<String, Object>>> before = databaseSnapshot();
		for (String route : OWNER_GET_ROUTES) {
			assertWrongRoleDenied(get(route).session(staff), route);
		}
		for (String route : OWNER_POST_ROUTES) {
			assertWrongRoleDenied(post(route).session(staff).with(csrf()), route);
		}
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-11 AC-12: other-owner and missing requests are identical and immutable")
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
