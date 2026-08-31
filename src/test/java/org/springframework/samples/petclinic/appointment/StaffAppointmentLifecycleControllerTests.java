package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.security.WebMvcPetClinicSecurity;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcPetClinicSecurity
@WebMvcTest(StaffAppointmentLifecycleController.class)
class StaffAppointmentLifecycleControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private StaffAppointmentLifecycleController controller;

	@MockitoBean
	private AppointmentRepository appointmentRepository;

	@MockitoBean
	private AppointmentChangeEventRepository appointmentChangeEventRepository;

	@MockitoBean
	private AppointmentOutcomeService outcomeService;

	@MockitoBean
	private AppointmentCorrectionService correctionService;

	@MockitoBean
	private StaffAppointmentReschedulingService reschedulingService;

	@MockitoBean
	private StaffAppointmentCancellationService cancellationService;

	@MockitoBean
	private OwnerRepository ownerRepository;

	@MockitoBean
	private VetRepository vetRepository;

	@MockitoBean
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@MockitoBean
	private AppointmentLifecyclePolicy lifecyclePolicy;

	@MockitoBean
	private Clock clock;

	@Test
	void cancellationFormRequiresContactWhenNoPriorAgreementExists() throws Exception {
		Appointment appointment = futureAppointment();
		given(this.appointmentRepository.findById(10L)).willReturn(Optional.of(appointment));
		given(this.appointmentChangeEventRepository.findByAppointmentIdOrderByOccurredAtAsc(10L)).willReturn(List.of());

		this.mockMvc.perform(get("/staff/appointments/10/cancel"))
			.andExpect(status().isOk())
			.andExpect(view().name("staff/appointments/cancel"))
			.andExpect(model().attribute("priorOwnerAgreement", false))
			.andExpect(content().string(containsString("id=\"ownerContactedCheck\" required=\"required\"")))
			.andExpect(content().string(containsString("required id=\"ownerExplanation\"")));
	}

	@Test
	void invalidCancellationRendersProcedureWithSafeValidationMessage() {
		Appointment appointment = futureAppointment();
		given(this.appointmentRepository.findById(10L)).willReturn(Optional.of(appointment));
		given(this.appointmentChangeEventRepository.findByAppointmentIdOrderByOccurredAtAsc(10L)).willReturn(List.of());
		willThrow(new IllegalArgumentException("An owner-facing cancellation explanation is required"))
			.given(this.cancellationService)
			.cancelAppointmentByStaff(any());
		StaffAppointmentLifecycleController.StaffCancellationForm form = new StaffAppointmentLifecycleController.StaffCancellationForm();
		form.setReasonCategory("VET_UNAVAILABLE");
		form.setReasonDetails("Veterinarian unavailable");
		form.setOwnerContacted(true);
		var principal = org.springframework.samples.petclinic.security.SecurityTestPrincipals.staff(7L, "admin");
		var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, "password",
				List.of(new SimpleGrantedAuthority("ROLE_STAFF")));
		ExtendedModelMap model = new ExtendedModelMap();

		String viewName = this.controller.processCancel(10L, form, authentication, model);

		assertThat(viewName).isEqualTo("staff/appointments/cancel");
		assertThat(model).containsEntry("errorMessage", "An owner-facing cancellation explanation is required")
			.containsEntry("priorOwnerAgreement", false);
	}

	private Appointment futureAppointment() {
		Instant startAt = Instant.parse("2026-09-02T10:00:00Z");
		Appointment appointment = new Appointment(1, 1, 1, startAt, startAt.plusSeconds(1800), "Europe/Amsterdam");
		appointment.setId(10L);
		return appointment;
	}

}
