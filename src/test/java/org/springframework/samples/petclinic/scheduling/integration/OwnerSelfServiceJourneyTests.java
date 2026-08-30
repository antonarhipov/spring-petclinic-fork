package org.springframework.samples.petclinic.scheduling.integration;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationPort;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnerSelfServiceJourneyTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AppointmentRepository appointments;

	@Autowired
	SchedulingRequestRepository requests;

	@MockitoBean
	InterpretationPort interpretationPort;

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownerSeesOwnHistoryAndCannotSeeOtherOwnerAppointment() throws Exception {
		Appointment own = persistAppointment(1, Instant.parse("2030-03-16T15:00:00Z"));
		Appointment other = persistAppointment(2, Instant.parse("2030-03-16T16:00:00Z"));
		this.mockMvc.perform(get("/owner/history"))
			.andExpect(status().isOk())
			.andExpect(view().name("scheduling/owner/history"));
		this.mockMvc.perform(get("/owner/appointments/" + own.getId())).andExpect(status().isOk());
		this.mockMvc.perform(get("/owner/appointments/" + other.getId())).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/owner/pets/2")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void cancelBeforeStartLeavesOriginalRequestClosed() throws Exception {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(1);
		request.setState(RequestState.CONFIRMED);
		request.setOwnerStatusCode("CONFIRMED");
		request.setCreatedAt(Instant.parse("2030-03-16T12:00:00Z"));
		request.setUpdatedAt(Instant.parse("2030-03-16T12:00:00Z"));
		request = this.requests.saveAndFlush(request);
		Appointment appointment = persistAppointment(1, Instant.parse("2030-03-16T18:00:00Z"));
		appointment.setRequestId(request.getId());
		appointment = this.appointments.saveAndFlush(appointment);
		this.mockMvc
			.perform(post("/owner/appointments/{id}/cancel", appointment.getId()).with(csrf())
				.param("reason", "cannot attend"))
			.andExpect(status().is3xxRedirection());
		assertThat(this.appointments.findById(appointment.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.CANCELLED);
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.CLOSED);
	}

	private Appointment persistAppointment(int petId, Instant start) {
		Appointment appointment = new Appointment();
		appointment.setPetId(petId);
		appointment.setVeterinarianId(1);
		appointment.setStartAt(start);
		appointment.setEndAt(start.plusSeconds(1800));
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setAuthorizationBasis("OWNER_ACCEPT");
		appointment.setCreatedAt(start);
		appointment.setUpdatedAt(start);
		return this.appointments.saveAndFlush(appointment);
	}

}
