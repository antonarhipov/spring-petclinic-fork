package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerHttpSurfaceTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void uc1Rule14StatusResponseContainsExactlyTheOwnedRequestState() throws Exception {
		int id = insertRequest(1, "owned request", "AWAITING_CONSENT");

		this.mvc.perform(get("/my/requests/{id}/status", id).with(user("george").roles("OWNER")))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().string("{\"state\":\"AWAITING_CONSENT\"}"));
	}

	@Test
	void uc1ExtensionsForeignUnknownWrongRoleAndAnonymousStatusAccessChangeNothing() throws Exception {
		int foreignId = insertRequest(3, "foreign secret request", "ABANDONED");
		List<Map<String, Object>> before = requestRows();

		MockHttpServletResponse foreign = this.mvc
			.perform(get("/my/requests/{id}/status", foreignId).with(user("george").roles("OWNER")))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse();
		MockHttpServletResponse unknown = this.mvc
			.perform(get("/my/requests/999999/status").with(user("george").roles("OWNER")))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse();

		assertThat(foreign.getContentAsByteArray()).isEqualTo(unknown.getContentAsByteArray()).isNotEmpty();
		assertThat(foreign.getContentAsString()).contains("The requested page was not found.")
			.doesNotContain("foreign secret request");
		this.mvc.perform(get("/my/requests/{id}/status", foreignId).with(user("staff").roles("STAFF")))
			.andExpect(status().isForbidden())
			.andExpect(content().string(""));
		this.mvc.perform(get("/my/requests/{id}/status", foreignId))
			.andExpect(status().isFound())
			.andExpect(redirectedUrl("/login"));
		assertThat(requestRows()).isEqualTo(before);
	}

	private int insertRequest(int petId, String text, String state) {
		this.jdbc.update("""
				insert into scheduling_requests
				(pet_id, active_pet_id, request_text, state, failure_count,
				 created_date, created_time, version)
				values (?, null, ?, ?, 0, DATE '2026-09-07', TIME '09:00:00', 0)
				""", petId, text, state);
		return this.jdbc.queryForObject("select max(id) from scheduling_requests", Integer.class);
	}

	private List<Map<String, Object>> requestRows() {
		return this.jdbc.queryForList("select * from scheduling_requests order by id");
	}

}
