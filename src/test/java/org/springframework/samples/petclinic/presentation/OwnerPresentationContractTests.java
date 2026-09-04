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

package org.springframework.samples.petclinic.presentation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.CareType;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.interpretation.StubRequestInterpreter;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class OwnerPresentationContractTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private RequestInterpretationService interpretationService;

	@Autowired
	private StubRequestInterpreter interpreter;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
		this.interpreter.clear();
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-7 AC-19: every owner page shows identity, logout, and urgent phone")
	void everyOwnerPageShowsIdentityLogoutAndBanner_AC7_AC19() throws Exception {
		MockHttpSession owner = loginOwner();
		SchedulingRequest request = createRequest();
		for (String path : List.of("/my/pets", "/my/appointments", "/my/requests/new",
				"/my/requests/" + request.getId())) {
			this.mockMvc.perform(get(path).session(owner))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("george")))
				.andExpect(content().string(containsString("action=\"/logout\"")))
				.andExpect(content().string(containsString("555-0199")));
		}
	}

	@Test
	void logoutInvalidatesSession_AC7() throws Exception {
		MockHttpSession owner = loginOwner();
		this.mockMvc.perform(post("/logout").session(owner).with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login?logout"));
		assertThat(owner.isInvalid()).isTrue();
	}

	@Test
	@org.junit.jupiter.api.DisplayName("AC-16 AC-17: owner pages reuse stock layout and My pets is read-only")
	void ownerPagesReuseStockLayoutWithoutEditControls_AC16_AC17() throws Exception {
		MockHttpSession owner = loginOwner();
		for (String path : List.of("/my/pets", "/my/appointments", "/my/requests/new")) {
			String html = this.mockMvc.perform(get(path).session(owner))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
			assertThat(html).contains("/resources/css/petclinic.css", "navbar navbar-expand-lg navbar-dark")
				.doesNotContain("style=");
			assertThat(count(html, "/resources/css/petclinic.css")).isOne();
		}

		String pets = this.mockMvc.perform(get("/my/pets").session(owner))
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(pets).contains("George", "Franklin", "Leo", "action=\"/logout\"")
			.doesNotContain("/owners/", "/pets/", "/visits/", ">Edit<", ">Add Visit<");
		assertThat(count(pets, "<form")).as("the session logout is the only form on the read-only page").isOne();
	}

	@Test
	void requestPageAloneExposesVetNamesAndSpecialties_AC18() throws Exception {
		MockHttpSession owner = loginOwner();
		SchedulingRequest request = createRequest();
		this.interpreter.setNextResult(new InterpretationResult(
				"Surgery review", 60, CareType.SPECIALTY, "surgery", 1, false, List.of(AvailabilityWindow
					.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), LocalTime.of(17, 0), "anytime")),
				"{}", "stub-model", "1.0"));
		this.lifecycleService.consent(request, "george");
		this.interpretationService.interpret(request, "george");

		String detail = getHtml("/my/requests/" + request.getId(), owner);
		assertThat(detail).contains("James Carter", "surgery")
			.doesNotContain("href=\"/owners/find\"", "href=\"/vets.html\"");
		for (String path : List.of("/my/pets", "/my/appointments", "/my/requests/new")) {
			assertThat(getHtml(path, owner)).doesNotContain("James Carter", "surgery", "href=\"/owners/find\"",
					"href=\"/vets.html\"");
		}
	}

	private SchedulingRequest createRequest() {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		return this.lifecycleService.createRequest(owner, pet, "Routine check", "Tuesday afternoon", "george");
	}

	private MockHttpSession loginOwner() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("george").password("george123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

	private String getHtml(String path, MockHttpSession session) throws Exception {
		return this.mockMvc.perform(get(path).session(session))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static int count(String text, String needle) {
		return (text.length() - text.replace(needle, "").length()) / needle.length();
	}

}
