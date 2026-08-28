package org.springframework.samples.petclinic.scheduling.availability;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentSource;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClinicDateTimeRenderingTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private PetRepository pets;

	@Autowired
	private VetRepository vets;

	@Test
	void rendersAppointmentTimesInTheClinicZone() throws Exception {
		Instant startAt = Instant.parse("2029-08-28T17:41:45.066677Z");
		this.appointments
			.saveAndFlush(new Appointment(this.pets.findById(1).orElseThrow(), this.vets.findById(1).orElseThrow(),
					startAt, 30, AppointmentSource.STAFF_OPERATIONAL, null, null, null, null));

		this.mvc.perform(get("/staff/calendar").with(user("admin").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Tuesday, August 28, 2029")))
			.andExpect(content().string(containsString("7:41")))
			.andExpect(content().string(containsString("CEST")))
			.andExpect(content().string(not(containsString(startAt.toString()))));
	}

}
