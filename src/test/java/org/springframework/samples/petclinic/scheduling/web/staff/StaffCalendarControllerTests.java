package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.SecurityConfiguration;
import org.springframework.samples.petclinic.scheduling.appointment.StaffCalendarQueryService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StaffCalendarController.class)
@Import(SecurityConfiguration.class)
class StaffCalendarControllerTests {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	StaffCalendarQueryService calendar;

	@MockitoBean
	AccountRepository accounts;

	@Test
	@WithMockUser(roles = "STAFF")
	void calendarLoads() throws Exception {
		given(this.calendar.appointmentsOn(any(LocalDate.class))).willReturn(List.of());
		this.mockMvc.perform(get("/staff/calendar")).andExpect(status().isOk());
	}

}
