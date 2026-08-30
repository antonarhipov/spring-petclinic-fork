package org.springframework.samples.petclinic.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRouteMatrixTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void unauthenticatedCatalogRedirectsToLogin() throws Exception {
		this.mockMvc.perform(get("/owners/1").accept(MediaType.TEXT_HTML))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(roles = "OWNER")
	void ownerCannotAccessStaffCatalog() throws Exception {
		this.mockMvc.perform(get("/owners/1")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCannotAccessOwnerApi() throws Exception {
		this.mockMvc.perform(get("/api/owner/scheduling-requests/1/status")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "STAFF")
	void staffCanAccessCatalog() throws Exception {
		this.mockMvc.perform(get("/vets.html")).andExpect(status().isOk());
	}

}
