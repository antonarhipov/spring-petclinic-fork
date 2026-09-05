/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.scheduling.clinic.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class AvailabilityPrecedenceTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private VetAvailabilityService availabilityService;

	@Autowired
	private EffectiveAvailabilityService effectiveAvailabilityService;

	@Autowired
	private VetWeeklyBlockRepository weeklyBlockRepository;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-100")
	void allAvailabilityShapesPersist_AC100() throws Exception {
		Map<DayOfWeek, List<VetAvailabilityService.TimeInterval>> schedule = new LinkedHashMap<>();
		schedule.put(DayOfWeek.MONDAY, List.of(interval("08:00", "11:00"), interval("13:00", "18:00")));
		List<VetAvailabilityService.VetExceptionDto> exceptions = List.of(
				new VetAvailabilityService.VetExceptionDto(date("2026-09-14"), false, time("10:00"), time("12:00")),
				new VetAvailabilityService.VetExceptionDto(date("2026-09-14"), false, time("14:00"), time("16:00")),
				new VetAvailabilityService.VetExceptionDto(date("2026-09-21"), true, null, null));
		List<VetAvailabilityService.VetLeaveDto> leaves = List
			.of(new VetAvailabilityService.VetLeaveDto(date("2026-09-28"), date("2026-09-30"), "Annual leave"));
		List<VetAvailabilityService.ClinicClosureDto> closures = List
			.of(new VetAvailabilityService.ClinicClosureDto(date("2026-10-05"), "Maintenance"));

		this.availabilityService.saveSchedule(1, schedule, exceptions, leaves, closures);
		VetAvailabilityService.VetAvailabilityData reloaded = this.availabilityService.getAvailabilityData(1);

		assertThat(reloaded.weeklyBlocks().stream().map(this::weeklyTuple).toList())
			.containsExactly("MONDAY|08:00|11:00", "MONDAY|13:00|18:00");
		assertThat(reloaded.exceptions()
			.stream()
			.map(exception -> exception.getExceptionDate() + "|" + exception.isUnavailable() + "|"
					+ exception.getStartTime() + "|" + exception.getEndTime())
			.toList()).containsExactly("2026-09-14|false|10:00|12:00", "2026-09-14|false|14:00|16:00",
					"2026-09-21|true|null|null");
		assertThat(reloaded.leaves()
			.stream()
			.map(leave -> leave.getStartDate() + "|" + leave.getEndDate() + "|" + leave.getReason())
			.toList()).containsExactly("2026-09-28|2026-09-30|Annual leave");
		assertThat(reloaded.closures()
			.stream()
			.map(closure -> closure.getClosureDate() + "|" + closure.getReason())
			.toList()).containsExactly("2026-10-05|Maintenance");

		String html = this.mockMvc.perform(get("/staff/vets/1/availability").session(loginStaff()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(html).contains("08:00", "11:00", "13:00", "18:00", "2026-09-14", "2026-09-21", "2026-09-28",
				"2026-09-30", "Annual leave", "2026-10-05", "Maintenance");
	}

	@Test
	@Tag("AC-101")
	void closureLeaveExceptionWeeklyPrecedence_AC101() {
		Map<DayOfWeek, List<VetAvailabilityService.TimeInterval>> schedule = Map.of(DayOfWeek.MONDAY,
				List.of(interval("07:00", "20:00")));
		List<VetAvailabilityService.VetExceptionDto> exceptions = List.of(
				new VetAvailabilityService.VetExceptionDto(date("2026-09-14"), false, time("08:00"), time("10:00")),
				new VetAvailabilityService.VetExceptionDto(date("2026-09-14"), false, time("16:00"), time("19:00")),
				new VetAvailabilityService.VetExceptionDto(date("2026-09-21"), true, null, null));
		List<VetAvailabilityService.VetLeaveDto> leaves = List
			.of(new VetAvailabilityService.VetLeaveDto(date("2026-09-27"), date("2026-09-28"), "Inclusive leave"));
		List<VetAvailabilityService.ClinicClosureDto> closures = List
			.of(new VetAvailabilityService.ClinicClosureDto(date("2026-10-05"), "Closure"));
		this.availabilityService.saveSchedule(1, schedule, exceptions, leaves, closures);

		assertThat(effective("2026-09-07")).containsExactly("09:00-17:00");
		assertThat(effective("2026-09-14")).containsExactly("09:00-10:00", "16:00-17:00");
		assertThat(effective("2026-09-21")).isEmpty();
		assertThat(effective("2026-09-27")).isEmpty();
		assertThat(effective("2026-09-28")).isEmpty();
		assertThat(effective("2026-10-05")).isEmpty();
	}

	@Test
	@Tag("AC-107")
	void outsideHoursAcceptedWarnedAndIntersected_AC107() throws Exception {
		this.mockMvc
			.perform(post("/staff/vets/1/availability").session(loginStaff())
				.with(csrf())
				.param("weeklySchedule[MONDAY_1_active]", "true")
				.param("weeklySchedule[MONDAY_1_start]", "07:00")
				.param("weeklySchedule[MONDAY_1_end]", "12:00")
				.param("weeklySchedule[MONDAY_2_active]", "true")
				.param("weeklySchedule[MONDAY_2_start]", "13:00")
				.param("weeklySchedule[MONDAY_2_end]", "21:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/vets/1/availability"))
			.andExpect(flash().attribute("warning", "outOfHoursWarning"));

		assertThat(this.weeklyBlockRepository.findByVetId(1).stream().map(this::weeklyTuple).toList())
			.containsExactly("MONDAY|07:00|12:00", "MONDAY|13:00|21:00");
		assertThat(effective("2026-09-07")).containsExactly("09:00-12:00", "13:00-17:00");
	}

	private List<String> effective(String isoDate) {
		return this.effectiveAvailabilityService.effectiveBlocks(1, date(isoDate))
			.stream()
			.map(interval -> interval.start() + "-" + interval.end())
			.toList();
	}

	private String weeklyTuple(VetWeeklyBlock block) {
		return block.getDayOfWeek() + "|" + block.getStartTime() + "|" + block.getEndTime();
	}

	private VetAvailabilityService.TimeInterval interval(String start, String end) {
		return new VetAvailabilityService.TimeInterval(time(start), time(end));
	}

	private LocalTime time(String value) {
		return LocalTime.parse(value);
	}

	private LocalDate date(String value) {
		return LocalDate.parse(value);
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

}
