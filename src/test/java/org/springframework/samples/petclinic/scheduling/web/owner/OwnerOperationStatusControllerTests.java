package org.springframework.samples.petclinic.scheduling.web.owner;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.AccountRole;
import org.springframework.samples.petclinic.account.SecurityConfiguration;
import org.springframework.samples.petclinic.scheduling.request.OwnerOperationStatus;
import org.springframework.samples.petclinic.scheduling.request.OwnerOperationStatusService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerOperationStatusController.class)
@Import({ SecurityConfiguration.class, CurrentOwnerAccount.class })
class OwnerOperationStatusControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccountRepository accounts;

	@MockitoBean
	OwnerOperationStatusService statuses;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerSafeStatusOmitsInternals() throws Exception {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		UUID id = UUID.fromString("9f296de2-a4fe-4d40-8305-775c3299ae0d");
		given(this.statuses.status(1, 41L, id)).willReturn(new OwnerOperationStatus(id, "INTERPRETATION", "RUNNING",
				"We are reviewing your request.", 4, Instant.parse("2026-08-30T10:15:30Z"), 1000, null));
		this.mockMvc.perform(get("/api/owner/scheduling-requests/41/operations/" + id))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
			.andExpect(jsonPath("$.type").value("INTERPRETATION"))
			.andExpect(jsonPath("$.state").value("RUNNING"))
			.andExpect(jsonPath("$.nextUrl").isEmpty())
			.andExpect(jsonPath("$.errorClassification").doesNotExist())
			.andExpect(jsonPath("$.percentage").doesNotExist());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void crossOwnerOperationIsNotFound() throws Exception {
		Account account = new Account();
		account.setUsername("george");
		account.setRole(AccountRole.OWNER);
		account.setOwnerId(1);
		given(this.accounts.findByUsername("george")).willReturn(Optional.of(account));
		UUID id = UUID.randomUUID();
		given(this.statuses.status(1, 99L, id)).willThrow(new OwnerResourceNotFoundException());
		this.mockMvc.perform(get("/api/owner/scheduling-requests/99/operations/" + id))
			.andExpect(status().isNotFound());
	}

}
