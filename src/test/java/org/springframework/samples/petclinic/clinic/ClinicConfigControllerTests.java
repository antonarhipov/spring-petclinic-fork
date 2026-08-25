/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetDateException;
import org.springframework.samples.petclinic.vet.VetDateExceptionRepository;
import org.springframework.samples.petclinic.vet.VetDateExceptionType;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.vet.VetWeeklyShift;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClinicConfigControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClinicSettingsRepository clinicSettingsRepository;

	@Autowired
	private PartOfDayRepository partOfDayRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private VetWeeklyShiftRepository vetWeeklyShiftRepository;

	@Autowired
	private VetDateExceptionRepository vetDateExceptionRepository;

	@Autowired
	private ClinicClosureRepository clinicClosureRepository;

	@Test
	void staffCanViewAndEditClinicSettings() throws Exception {
		mockMvc.perform(get("/staff/clinic/settings").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("clinic/settings"));

		mockMvc
			.perform(post("/staff/clinic/settings").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("timeZone", "America/Chicago")
				.param("minVisitMinutes", "20")
				.param("maxVisitMinutes", "90")
				.param("defaultVisitMinutes", "45")
				.param("bookingHorizonDays", "21")
				.param("holdDurationMinutes", "10")
				.param("gridMinutes", "15"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/settings?success"));

		ClinicSettings updated = clinicSettingsRepository.getSettingsOrDefault();
		assertThat(updated.getTimeZone()).isEqualTo("America/Chicago");
		assertThat(updated.getMinVisitMinutes()).isEqualTo(20);
		assertThat(updated.getMaxVisitMinutes()).isEqualTo(90);
		assertThat(updated.getDefaultVisitMinutes()).isEqualTo(45);
		assertThat(updated.getBookingHorizonDays()).isEqualTo(21);
		assertThat(updated.getHoldDurationMinutes()).isEqualTo(10);
	}

	@Test
	void staffCanCreateAndDeletePartOfDay() throws Exception {
		mockMvc.perform(get("/staff/clinic/parts-of-day").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("clinic/partsOfDay"));

		mockMvc
			.perform(post("/staff/clinic/parts-of-day/new").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("name", "NIGHT")
				.param("startTime", "20:00")
				.param("endTime", "23:59"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/parts-of-day"));

		PartOfDay created = partOfDayRepository.findByNameIgnoreCase("NIGHT").orElseThrow();
		assertThat(created.getStartTime()).isEqualTo(LocalTime.of(20, 0));

		mockMvc
			.perform(post("/staff/clinic/parts-of-day/{id}/delete", created.getId()).with(user("staff1").roles("STAFF"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/parts-of-day"));

		assertThat(partOfDayRepository.findByNameIgnoreCase("NIGHT")).isEmpty();
	}

	@Test
	void staffCanCreateAndDeleteVetWeeklyShifts() throws Exception {
		mockMvc.perform(get("/staff/clinic/vets/1/shifts").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("clinic/vetShifts"));

		mockMvc
			.perform(post("/staff/clinic/vets/1/shifts/new").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("dayOfWeek", "SATURDAY")
				.param("startTime", "09:00")
				.param("endTime", "13:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/vets/1/shifts"));

		List<VetWeeklyShift> satShifts = vetWeeklyShiftRepository.findByVetIdAndDayOfWeek(1, DayOfWeek.SATURDAY);
		assertThat(satShifts).hasSize(1);
		int shiftId = satShifts.get(0).getId();

		mockMvc
			.perform(post("/staff/clinic/vets/{vetId}/shifts/{shiftId}/delete", 1, shiftId)
				.with(user("staff1").roles("STAFF"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/vets/1/shifts"));

		assertThat(vetWeeklyShiftRepository.findById(shiftId)).isEmpty();
	}

	@Test
	void staffCanCreateAndDeleteVetDateExceptions() throws Exception {
		mockMvc.perform(get("/staff/clinic/vets/1/exceptions").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("clinic/vetExceptions"));

		mockMvc
			.perform(post("/staff/clinic/vets/1/exceptions/new").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("startDate", "2026-10-10")
				.param("endDate", "2026-10-12")
				.param("type", "LEAVE")
				.param("reason", "Conference"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/vets/1/exceptions"));

		List<VetDateException> exceptions = vetDateExceptionRepository.findByVetId(1);
		assertThat(exceptions).isNotEmpty();
		VetDateException last = exceptions.get(exceptions.size() - 1);
		assertThat(last.getReason()).isEqualTo("Conference");

		mockMvc
			.perform(post("/staff/clinic/vets/{vetId}/exceptions/{id}/delete", 1, last.getId())
				.with(user("staff1").roles("STAFF"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/vets/1/exceptions"));

		assertThat(vetDateExceptionRepository.findById(last.getId())).isEmpty();
	}

	@Test
	void staffCanCreateAndDeleteClinicClosures() throws Exception {
		mockMvc.perform(get("/staff/clinic/closures").with(user("staff1").roles("STAFF")))
			.andExpect(status().isOk())
			.andExpect(view().name("clinic/closures"));

		mockMvc
			.perform(post("/staff/clinic/closures/new").with(user("staff1").roles("STAFF"))
				.with(csrf())
				.param("startDate", "2026-12-25")
				.param("endDate", "2026-12-25")
				.param("reason", "Christmas Day"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/closures"));

		List<ClinicClosure> closures = clinicClosureRepository.findAll();
		assertThat(closures).isNotEmpty();
		ClinicClosure closure = closures.get(closures.size() - 1);
		assertThat(closure.getReason()).isEqualTo("Christmas Day");

		mockMvc
			.perform(post("/staff/clinic/closures/{id}/delete", closure.getId()).with(user("staff1").roles("STAFF"))
				.with(csrf()))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/clinic/closures"));

		assertThat(clinicClosureRepository.findById(closure.getId())).isEmpty();
	}

	@Test
	void ownerCannotAccessStaffClinicConfig() throws Exception {
		mockMvc.perform(get("/staff/clinic/settings").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/staff/clinic/settings").with(user("owner1").roles("OWNER")).with(csrf()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/staff/clinic/parts-of-day").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/staff/clinic/vets/1/shifts").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/staff/clinic/vets/1/exceptions").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/staff/clinic/closures").with(user("owner1").roles("OWNER")))
			.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedUserRedirectedToLogin() throws Exception {
		mockMvc.perform(get("/staff/clinic/settings")).andExpect(status().is3xxRedirection());
	}

}
