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
class OwnerProfileIntegrationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void ownerCanViewTheirFullProfileAndOpenTheEditForm() throws Exception {
		this.mvc.perform(get("/my/profile").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("George Franklin")))
			.andExpect(content().string(containsString("110 W. Liberty St.")))
			.andExpect(content().string(containsString("Madison")))
			.andExpect(content().string(containsString("6085551023")))
			.andExpect(content().string(containsString("/my/profile/edit")))
			.andExpect(content().string(not(containsString("Betty Davis"))));

		this.mvc.perform(get("/my/profile/edit").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Edit profile")))
			.andExpect(content().string(containsString("value=\"George\"")))
			.andExpect(content().string(containsString("value=\"Franklin\"")))
			.andExpect(content().string(containsString("value=\"110 W. Liberty St.\"")))
			.andExpect(content().string(containsString("value=\"6085551023\"")))
			.andExpect(content().string(containsString("href=\"/my/profile\"")));
	}

	@Test
	void ownerCanUpdateTheirFullProfile() throws Exception {
		this.mvc
			.perform(post("/my/profile/edit").with(user("george").roles("OWNER"))
				.with(csrf())
				.param("firstName", "Georgina")
				.param("lastName", "Franklin")
				.param("address", "12 New Street")
				.param("city", "Middleton")
				.param("telephone", "6085559999"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/profile"));

		this.mvc.perform(get("/my/profile").with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Georgina Franklin")))
			.andExpect(content().string(containsString("12 New Street")))
			.andExpect(content().string(containsString("Middleton")))
			.andExpect(content().string(containsString("6085559999")));
	}

	@Test
	void profileRequiresTheOwnerRole() throws Exception {
		this.mvc.perform(get("/my/profile")).andExpect(status().is3xxRedirection());
		this.mvc.perform(get("/my/profile").with(user("admin").roles("STAFF"))).andExpect(status().isForbidden());
	}

}
