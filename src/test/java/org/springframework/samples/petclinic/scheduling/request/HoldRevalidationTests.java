/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.request;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.TestClockConfig;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(TestClockConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HoldRevalidationTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private RequestLifecycleService lifecycleService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private AppointmentLifecycleService appointmentLifecycleService;

	@Autowired
	private AppointmentRepository appointmentRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-62")
	void openingInvalidHoldReoffersOrHandsOff_AC62() throws Exception {
		SchedulingRequest request = interpretedRequest(1, "Sunday hold");
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime closedSunday = ZonedDateTime.parse("2026-09-13T10:00:00+02:00[Europe/Amsterdam]");
		this.lifecycleService.confirmFeasible(request, "george", vet, closedSunday, 30);

		this.mockMvc.perform(get("/my/requests/{id}", request.getId()).session(login("george", "george123")))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("That appointment slot is no longer available.")));

		SchedulingRequest revalidated = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(revalidated.getState()).isIn(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		if (revalidated.getState() == RequestState.SUGGESTION_OFFERED) {
			assertThat(revalidated.getHeldStart()).isNotEqualTo(closedSunday);
		}
		else {
			assertThat(revalidated.hasHold()).isFalse();
		}
	}

	@Test
	@Tag("AC-64")
	void lockedLostSlotReoffersWithoutError_AC64() throws Exception {
		SchedulingRequest request = interpretedRequest(1, "Lost slot");
		Owner otherOwner = this.ownerRepository.findById(2).orElseThrow();
		Pet otherPet = otherOwner.getPets().stream().findFirst().orElseThrow();
		Vet vet = this.vetRepository.findById(1).orElseThrow();
		ZonedDateTime heldStart = ZonedDateTime.parse("2026-09-08T10:00:00+02:00[Europe/Amsterdam]");
		this.lifecycleService.confirmFeasible(request, "george", vet, heldStart, 30);
		Appointment conflict = this.appointmentLifecycleService.bookAppointment(otherPet, vet, heldStart, 30,
				"conflicting booking", null, "staff");

		this.mockMvc
			.perform(post("/my/requests/{id}/accept", request.getId()).session(login("george", "george123"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/my/requests/" + request.getId()))
			.andExpect(flash().attribute("holdUnavailable", true));

		SchedulingRequest recovered = this.requestRepository.findById(request.getId()).orElseThrow();
		assertThat(recovered.getState()).isIn(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		assertThat(this.appointmentRepository.findAll()).extracting(Appointment::getId)
			.containsExactly(conflict.getId());
		if (recovered.getState() == RequestState.SUGGESTION_OFFERED) {
			assertThat(recovered.getHeldStart()).isNotEqualTo(heldStart);
		}
		else {
			assertThat(recovered.hasHold()).isFalse();
		}
	}

	private SchedulingRequest interpretedRequest(int ownerId, String reason) {
		Owner owner = this.ownerRepository.findById(ownerId).orElseThrow();
		Pet pet = owner.getPets().stream().findFirst().orElseThrow();
		SchedulingRequest request = this.lifecycleService.createRequest(owner, pet, reason, "any weekday", "george");
		this.lifecycleService.consent(request, "george");
		return this.lifecycleService.interpretationUsable(request, "system");
	}

	private MockHttpSession login(String username, String password) throws Exception {
		MvcResult result = this.mockMvc.perform(formLogin().user(username).password(password))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
