package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.security.WebMvcPetClinicSecurity;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcPetClinicSecurity
@WebMvcTest(AvailabilityController.class)
class AvailabilityControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AvailabilityAdministrationService availabilityAdministrationService;

	@MockitoBean
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@MockitoBean
	private RecurringShiftRepository recurringShiftRepository;

	@MockitoBean
	private AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	@MockitoBean
	private VeterinarianLeaveRepository veterinarianLeaveRepository;

	@MockitoBean
	private ClinicClosureRepository clinicClosureRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private HoldReleaseService holdReleaseService;

	@Test
	void showClinicPolicyReturnsViewWithModel() throws Exception {
		ClinicPolicy policy = new ClinicPolicy();
		given(this.effectiveAvailabilityService.getClinicPolicy()).willReturn(policy);

		this.mockMvc.perform(get("/staff/clinic-policy"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/clinic-policy"))
			.andExpect(model().attributeExists("policy"));
	}

	@Test
	void updateClinicPolicyRedirects() throws Exception {
		this.mockMvc
			.perform(post("/staff/clinic-policy").with(csrf())
				.param("bookingHorizonDays", "60")
				.param("holdDurationMinutes", "15")
				.param("ownerNoticeMinutes", "180")
				.param("startGridMinutes", "15")
				.param("clinicPhone", "555-0200")
				.param("contactHours", "Mon-Sat 08:00-18:00")
				.param("urgentCareGuidance", "Call emergency."))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic-policy"));

		verify(this.availabilityAdministrationService).updateClinicPolicy(any(ClinicPolicy.class), any());
	}

	@Test
	void listShiftsReturnsView() throws Exception {
		Vet vet = new Vet();
		vet.setId(1);
		vet.setFirstName("James");
		vet.setLastName("Carter");
		given(this.vetRepository.findAll()).willReturn(List.of(vet));

		this.mockMvc.perform(get("/staff/availability/shifts"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/availability/shifts"))
			.andExpect(model().attributeExists("vets", "shifts"));
	}

	@Test
	void createShiftSavesAndRedirects() throws Exception {
		this.mockMvc
			.perform(post("/staff/availability/shifts").with(csrf())
				.param("vetId", "1")
				.param("weekday", "MONDAY")
				.param("localStart", "09:00")
				.param("localEnd", "17:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/availability/shifts"));

		verify(this.availabilityAdministrationService).createRecurringShift(eq(1), eq(DayOfWeek.MONDAY),
				eq(LocalTime.of(9, 0)), eq(LocalTime.of(17, 0)), any());
	}

	@Test
	void listExceptionsReturnsView() throws Exception {
		this.mockMvc.perform(get("/staff/availability/exceptions"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/availability/exception-day"))
			.andExpect(model().attributeExists("vets", "exceptions", "exceptionForm"));
	}

	@Test
	void createLeaveSavesAndRedirects() throws Exception {
		this.mockMvc
			.perform(post("/staff/availability/leave").with(csrf())
				.param("vetId", "1")
				.param("startDate", "2026-09-10")
				.param("endDate", "2026-09-15")
				.param("reasonCategory", "VACATION")
				.param("internalNote", "Holiday"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/availability/leave"));

		verify(this.availabilityAdministrationService).createVeterinarianLeave(eq(1), eq(LocalDate.of(2026, 9, 10)),
				eq(LocalDate.of(2026, 9, 15)), eq("VACATION"), eq("Holiday"), any());
	}

	@Test
	void createClosureSavesAndRedirects() throws Exception {
		this.mockMvc
			.perform(post("/staff/availability/closures").with(csrf())
				.param("startDate", "2026-12-25")
				.param("endDate", "2026-12-26")
				.param("ownerReason", "Christmas")
				.param("internalNote", "Holiday"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/availability/closures"));

		verify(this.availabilityAdministrationService).createClinicClosure(eq(LocalDate.of(2026, 12, 25)),
				eq(LocalDate.of(2026, 12, 26)), eq("Christmas"), eq("Holiday"), any());
	}

}
