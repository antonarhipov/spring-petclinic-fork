package org.springframework.samples.petclinic.scheduling.web.staff;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.SecurityConfiguration;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityCommandService;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.scheduling.availability.ClinicPolicyService;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(StaffAvailabilityController.class)
@Import(SecurityConfiguration.class)
class StaffAvailabilityControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AvailabilityCommandService commands;

	@MockitoBean
	ClinicPolicyService policies;

	@MockitoBean
	AccountRepository accounts;

	@Test
	@WithMockUser(roles = "STAFF")
	void availabilityPageLoads() throws Exception {
		given(this.policies.current()).willReturn(new ClinicSchedulingPolicy());
		given(this.commands.closures()).willReturn(List.of());
		this.mockMvc.perform(get("/staff/availability")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void leaveConflictShowsPage() throws Exception {
		Account account = new Account();
		org.springframework.test.util.ReflectionTestUtils.setField(account, "id", 1L);
		given(this.accounts.findByUsername("admin")).willReturn(Optional.of(account));
		given(this.policies.current()).willReturn(new ClinicSchedulingPolicy());
		doThrow(new AvailabilityConflictException(List.of("APPOINTMENT:1"))).when(this.commands)
			.addLeave(anyInt(), any(LocalDate.class), any(LocalDate.class), any());
		this.mockMvc
			.perform(post("/staff/availability/leave").with(csrf())
				.param("veterinarianId", "1")
				.param("startLocalDate", "2026-03-16")
				.param("endLocalDate", "2026-03-16"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/staff/capacity-conflict"));
	}

}
