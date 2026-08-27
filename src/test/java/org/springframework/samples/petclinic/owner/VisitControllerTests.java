package org.springframework.samples.petclinic.owner;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.security.CustomAuthenticationFailureHandler;
import org.springframework.samples.petclinic.security.CustomAuthenticationSuccessHandler;
import org.springframework.samples.petclinic.security.WebMvcSecurityTestConfig;
import org.springframework.test.context.aot.DisabledInAotMode;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(WebMvcSecurityTestConfig.class)
@WebMvcTest(OwnerController.class)
@DisabledInNativeImage
@DisabledInAotMode
class VisitControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OwnerRepository owners;

	@MockitoBean
	private CustomAuthenticationSuccessHandler successHandler;

	@MockitoBean
	private CustomAuthenticationFailureHandler failureHandler;

	@Test
	void testLegacyNewVisitGetRouteIsRemoved() throws Exception {
		mockMvc.perform(get("/owners/1/pets/1/visits/new").with(user("staff1").roles("STAFF")))
			.andExpect(status().isNotFound());
	}

	@Test
	void testLegacyNewVisitPostRouteIsRemoved() throws Exception {
		mockMvc
			.perform(post("/owners/1/pets/1/visits/new").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("date", "2026-08-30")
				.param("description", "Routine Checkup"))
			.andExpect(status().isNotFound());
	}

}
