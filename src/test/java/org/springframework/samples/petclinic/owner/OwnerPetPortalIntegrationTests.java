package org.springframework.samples.petclinic.owner;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerPetPortalIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void ownerCanSeeOnlyTheirPetsAndPetManagementActions() throws Exception {
		this.mvc.perform(get("/my/pets").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leo")))
			.andExpect(content().string(not(containsString("Basil"))))
			.andExpect(content().string(containsString("/my/pets/new")))
			.andExpect(content().string(containsString("/my/pets/1/edit")));

		this.mvc.perform(get("/my/pets/new").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Add Pet")))
			.andExpect(content().string(containsString("href=\"/my/pets\"")));

		this.mvc.perform(get("/my/pets/1/edit").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Update Pet")))
			.andExpect(content().string(containsString("value=\"Leo\"")));
	}

	@Test
	void ownerCanAddAndEditAPet() throws Exception {
		this.mvc
			.perform(post("/my/pets/new").with(user("george").roles("OWNER"))
				.with(csrf())
				.param("name", "Comet")
				.param("type", "cat")
				.param("birthDate", "2020-02-12"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/pets"));

		this.mvc.perform(get("/my/pets").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Comet")));

		this.mvc
			.perform(post("/my/pets/1/edit").with(user("george").roles("OWNER"))
				.with(csrf())
				.param("name", "Leonard")
				.param("type", "cat")
				.param("birthDate", "2010-09-07"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/pets"));

		this.mvc.perform(get("/my/pets").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Leonard")));
	}

	@Test
	void ownerCannotEditAnotherOwnersPet() throws Exception {
		this.mvc.perform(get("/my/pets/2/edit").with(user("george").roles("OWNER"))).andExpect(status().isNotFound());
	}

	@Test
	void petPortalRequiresTheOwnerRole() throws Exception {
		this.mvc.perform(get("/my/pets")).andExpect(status().is3xxRedirection());
		this.mvc.perform(get("/my/pets").with(user("admin").roles("STAFF"))).andExpect(status().isForbidden());
	}

}
