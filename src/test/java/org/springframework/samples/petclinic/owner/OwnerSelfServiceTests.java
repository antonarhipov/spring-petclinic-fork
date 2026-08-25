/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.owner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.security.UserAccount;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.samples.petclinic.security.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerSelfServiceTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private SchedulingRequestRepository schedulingRequests;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private Owner owner1;

	private Owner owner2;

	private Pet owner1Pet;

	private Pet owner2Pet;

	private SchedulingRequest owner2Request;

	@BeforeEach
	void setUp() {
		this.owner1 = this.owners.findById(1).orElseThrow();
		this.owner2 = this.owners.findById(2).orElseThrow();
		this.owner1Pet = this.owner1.getPets().get(0);
		this.owner2Pet = this.owner2.getPets().get(0);

		UserAccount user1 = new UserAccount("owner1_user", this.passwordEncoder.encode("pass123"), UserRole.OWNER,
				false, this.owner1);
		this.userAccountRepository.saveAndFlush(user1);

		UserAccount user2 = new UserAccount("owner2_user", this.passwordEncoder.encode("pass123"), UserRole.OWNER,
				false, this.owner2);
		this.userAccountRepository.saveAndFlush(user2);

		SchedulingRequest req = new SchedulingRequest();
		req.setOwner(this.owner2);
		req.setPet(this.owner2Pet);
		req.setRawText("Checkup for owner 2 pet");
		req.setState(RequestState.DRAFT);
		this.owner2Request = this.schedulingRequests.saveAndFlush(req);
	}

	@Test
	void ownerCanViewOwnProfile() throws Exception {
		this.mockMvc.perform(get("/my-profile").with(user("owner1_user").roles("OWNER"))).andExpect(status().isOk());
	}

	@Test
	void ownerCanEditOwnProfile() throws Exception {
		this.mockMvc
			.perform(post("/my-profile/edit").with(user("owner1_user").roles("OWNER"))
				.with(csrf())
				.param("firstName", "GeorgeUpdated")
				.param("lastName", "Franklin")
				.param("address", "123 New Address")
				.param("city", "Madison")
				.param("telephone", "6085551234"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-profile"));

		Owner updated = this.owners.findById(1).orElseThrow();
		assertThat(updated.getFirstName()).isEqualTo("GeorgeUpdated");
		assertThat(updated.getAddress()).isEqualTo("123 New Address");
	}

	@Test
	void ownerCanCreateAndEditOwnPet() throws Exception {
		this.mockMvc
			.perform(post("/my-pets/new").with(user("owner1_user").roles("OWNER"))
				.with(csrf())
				.param("name", "Buddy")
				.param("birthDate", "2020-01-01")
				.param("type", "dog"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-profile"));

		Owner updated = this.owners.findById(1).orElseThrow();
		assertThat(updated.getPet("Buddy")).isNotNull();

		this.mockMvc
			.perform(post("/my-pets/" + this.owner1Pet.getId() + "/edit").with(user("owner1_user").roles("OWNER"))
				.with(csrf())
				.param("name", "LeoRenamed")
				.param("birthDate", "2010-09-07")
				.param("type", "cat"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my-profile"));

		Owner reloaded = this.owners.findById(1).orElseThrow();
		assertThat(reloaded.getPet("LeoRenamed")).isNotNull();
	}

	@Test
	void ownerAttemptingToEditAnotherOwnersPetReceives403() throws Exception {
		this.mockMvc
			.perform(get("/my-pets/" + this.owner2Pet.getId() + "/edit").with(user("owner1_user").roles("OWNER")))
			.andExpect(status().isForbidden());

		this.mockMvc
			.perform(post("/my-pets/" + this.owner2Pet.getId() + "/edit").with(user("owner1_user").roles("OWNER"))
				.with(csrf())
				.param("name", "HackedName")
				.param("birthDate", "2020-01-01")
				.param("type", "dog"))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerAttemptingToAccessAnotherOwnersSchedulingRequestReceives403() throws Exception {
		this.mockMvc
			.perform(get("/scheduling/requests/" + this.owner2Request.getId()).with(user("owner1_user").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerAttemptingIdTamperingOnSchedulingCreationReceives403() throws Exception {
		this.mockMvc
			.perform(get("/owners/" + this.owner2.getId() + "/pets/" + this.owner2Pet.getId() + "/schedule/new")
				.with(user("owner1_user").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void ownerCannotAccessStaffOwnersCrudZone() throws Exception {
		this.mockMvc.perform(get("/owners/1").with(user("owner1_user").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

}
