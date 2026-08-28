package org.springframework.samples.petclinic.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void protectsOwnerAndStaffOperationalRoutes() throws Exception {
		this.mvc.perform(get("/my/appointments")).andExpect(status().is3xxRedirection());
		MockHttpSession ownerSession = signIn("george", "george123");
		this.mvc.perform(get("/staff/calendar").session(ownerSession)).andExpect(status().isForbidden());
		this.mvc.perform(post("/my/scheduling/requests").session(signIn("george", "george123")))
			.andExpect(status().is3xxRedirection());
		this.mvc.perform(get("/staff/calendar").session(signIn("admin", "admin123"))).andExpect(status().isOk());
	}

	@Test
	void acceptsCsrfProtectedOwnerActions() throws Exception {
		this.mvc
			.perform(post("/my/scheduling/requests").session(signIn("george", "george123"))
				.with(csrf())
				.param("petId", "1")
				.param("sourceText", "A sufficiently detailed appointment request.")
				.param("consent", "false"))
			.andExpect(redirectedUrl("/my/appointments"));
	}

	@Test
	void ownerCanOpenTheNewSchedulingRequestPage() throws Exception {
		this.mvc.perform(get("/my/scheduling/requests/new").session(signIn("george", "george123")))
			.andExpect(status().isOk());
	}

	private MockHttpSession signIn(String username, String password) throws Exception {
		MvcResult result = this.mvc
			.perform(post("/login").with(csrf()).param("username", username).param("password", password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
