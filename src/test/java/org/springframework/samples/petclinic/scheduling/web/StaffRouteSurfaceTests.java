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

import java.time.ZonedDateTime;
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
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
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

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class StaffRouteSurfaceTests {

	private static final List<String> MUTABLE_TABLES = List.of("scheduling_request", "scheduling_request_event",
			"interpretation", "interpretation_window", "appointment", "appointment_change");

	@Autowired
	private WebApplicationContext context;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-84")
	void everyStaffRouteResolvesForStaff() throws Exception {
		// 1. Assert GET /staff/queue maps to exactly one handler method and that handler
		// is StaffQueueController
		int queueMappingsCount = 0;
		Class<?> queueHandlerBeanType = null;
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : this.handlerMapping.getHandlerMethods().entrySet()) {
			RequestMappingInfo info = entry.getKey();
			Set<String> patterns = info.getPatternValues();
			if (patterns.contains("/staff/queue")) {
				queueMappingsCount++;
				queueHandlerBeanType = entry.getValue().getBeanType();
			}
		}
		assertThat(queueMappingsCount).as("GET /staff/queue must resolve to exactly one handler method").isEqualTo(1);
		assertThat(queueHandlerBeanType)
			.as("GET /staff/queue must be handled by StaffQueueController and no mapping left in StaffPageController")
			.isEqualTo(StaffQueueController.class);

		// 2. Create backing request for parameterized routes
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().get(0);
		SchedulingRequest req = new SchedulingRequest();
		req.setOwner(owner);
		req.setPet(pet);
		req.setState(RequestState.WITH_STAFF);
		req.setReasonText("Surface test request");
		req.setAvailabilityText("anytime");
		req.setCreatedAt(ZonedDateTime.now());
		req.setUpdatedAt(ZonedDateTime.now());
		req = this.requestRepository.saveAndFlush(req);
		int reqId = req.getId();

		List<String> getRoutes = List.of("/staff/queue", "/staff/requests/" + reqId,
				"/staff/requests/" + reqId + "/interpretation", "/staff/appointments/new");

		// Perform all Phase 3 GET and POST routes as authenticated staff
		MockHttpSession staff = login("staff", "staff123");

		for (String route : getRoutes) {
			this.mockMvc.perform(get(route).session(staff)).andExpect(status().isOk());
		}

		this.mockMvc.perform(post("/staff/requests/" + reqId + "/interpretation").session(staff).with(csrf()))
			.andExpect(status().is3xxRedirection());

		this.mockMvc.perform(post("/staff/requests/" + reqId + "/suggest").session(staff).with(csrf()))
			.andExpect(status().is3xxRedirection());

		this.mockMvc.perform(post("/staff/requests/" + reqId + "/release-hold").session(staff).with(csrf()))
			.andExpect(status().is3xxRedirection());

		this.mockMvc.perform(post("/staff/appointments").session(staff).with(csrf()))
			.andExpect(status().is3xxRedirection());
	}

	@Test
	@Tag("AC-9")
	void ownerAndAnonymousDeniedWithoutDisclosureOrMutation() throws Exception {
		Map<String, List<Map<String, Object>>> before = databaseSnapshot();

		List<String> getRoutes = List.of("/staff/queue", "/staff/requests/1", "/staff/requests/1/interpretation",
				"/staff/appointments/new");

		List<String> postRoutes = List.of("/staff/requests/1/interpretation", "/staff/requests/1/suggest",
				"/staff/requests/1/release-hold", "/staff/appointments");

		// Anonymous checks
		for (String route : getRoutes) {
			this.mockMvc.perform(get(route))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/login")));
		}
		for (String route : postRoutes) {
			this.mockMvc.perform(post(route).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", endsWith("/login")));
		}

		// Owner checks
		MockHttpSession owner = login("george", "george123");
		for (String route : getRoutes) {
			this.mockMvc.perform(get(route).session(owner))
				.andExpect(status().isForbidden())
				.andExpect(content().string(noStaffProtectedData()));
		}
		for (String route : postRoutes) {
			this.mockMvc.perform(post(route).session(owner).with(csrf()))
				.andExpect(status().isForbidden())
				.andExpect(content().string(noStaffProtectedData()));
		}

		// Verify database was untouched
		assertThat(databaseSnapshot()).isEqualTo(before);
	}

	private static org.hamcrest.Matcher<String> noStaffProtectedData() {
		return allOf(not(containsString("Needs staff")), not(containsString("All open")),
				not(containsString("Save interpretation")), not(containsString("Direct booking")));
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
