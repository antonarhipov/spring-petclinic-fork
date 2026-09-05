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

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigService;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDay;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicSettingsService;
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
class ClinicSettingsTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ClinicSettingsService settingsService;

	@Autowired
	private Clock clock;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context).apply(springSecurity()).build();
	}

	@Test
	@Tag("AC-99")
	void formStartsWithEverySeededDefault_AC99() throws Exception {
		MockHttpSession staffSession = loginStaff();
		ClinicConfig current = this.settingsService.current();
		List<ClinicOpeningHour> openingHours = this.settingsService.allOpeningHours();
		List<ClinicPartOfDay> partsOfDay = this.settingsService.allPartsOfDay();

		assertThat(current.getBookingHorizonDays()).isEqualTo(30);
		assertThat(current.getMinDurationMinutes()).isEqualTo(15);
		assertThat(current.getMaxDurationMinutes()).isEqualTo(60);
		assertThat(current.getDefaultDurationMinutes()).isEqualTo(30);
		assertThat(current.getEmergencyPhone()).isEqualTo("555-0199");
		assertThat(current.getTimeZone()).isEqualTo("Europe/Amsterdam");
		assertThat(openingHours.stream().map(this::openingHourTuple).toList()).containsExactly(
				"MONDAY|09:00|17:00|false", "TUESDAY|09:00|17:00|false", "WEDNESDAY|09:00|18:00|false",
				"THURSDAY|09:00|17:00|false", "FRIDAY|10:00|16:00|false", "SATURDAY|null|null|true",
				"SUNDAY|null|null|true");
		assertThat(partsOfDay.stream().map(this::partOfDayTuple).toList()).containsExactly("morning|09:00|12:00",
				"afternoon|12:00|17:00", "evening|17:00|18:00");

		MvcResult result = this.mockMvc.perform(get("/staff/settings").session(staffSession))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();

		for (ClinicOpeningHour h : openingHours) {
			assertThat(html).contains(h.getDayOfWeek().name());
			if (!h.isClosed() && h.getOpenTime() != null) {
				assertThat(html).contains(h.getOpenTime().toString());
			}
		}

		assertThat(html).contains(String.valueOf(current.getMinDurationMinutes()));
		assertThat(html).contains(String.valueOf(current.getMaxDurationMinutes()));
		assertThat(html).contains(String.valueOf(current.getDefaultDurationMinutes()));
		assertThat(html).contains(String.valueOf(current.getBookingHorizonDays()));
		assertThat(html).contains(current.getEmergencyPhone());
		assertThat(html).contains(current.getTimeZone());

		for (ClinicPartOfDay part : partsOfDay) {
			assertThat(html).contains(part.getStartTime().toString());
			assertThat(html).contains(part.getEndTime().toString());
		}
	}

	@Test
	@Tag("AC-99")
	void validEditPersistsEveryFieldAndUpdatesClock_AC99() throws Exception {
		MockHttpSession staffSession = loginStaff();

		this.mockMvc
			.perform(post("/staff/settings").session(staffSession)
				.with(csrf())
				.param("horizonDays", "45")
				.param("minDurationMinutes", "20")
				.param("maxDurationMinutes", "90")
				.param("defaultDurationMinutes", "45")
				.param("emergencyPhone", "555-0999")
				.param("timeZone", "Europe/Tallinn")
				.param("openingHours[MONDAY_open]", "08:30")
				.param("openingHours[MONDAY_close]", "17:30")
				.param("openingHours[MONDAY_closed]", "false")
				.param("openingHours[TUESDAY_open]", "08:30")
				.param("openingHours[TUESDAY_close]", "17:30")
				.param("openingHours[TUESDAY_closed]", "false")
				.param("openingHours[WEDNESDAY_open]", "08:30")
				.param("openingHours[WEDNESDAY_close]", "17:30")
				.param("openingHours[WEDNESDAY_closed]", "false")
				.param("openingHours[THURSDAY_open]", "08:30")
				.param("openingHours[THURSDAY_close]", "17:30")
				.param("openingHours[THURSDAY_closed]", "false")
				.param("openingHours[FRIDAY_open]", "08:30")
				.param("openingHours[FRIDAY_close]", "17:30")
				.param("openingHours[FRIDAY_closed]", "false")
				.param("openingHours[SATURDAY_closed]", "true")
				.param("openingHours[SUNDAY_closed]", "true")
				.param("morningStart", "08:30")
				.param("morningEnd", "12:00")
				.param("afternoonStart", "12:00")
				.param("afternoonEnd", "17:00")
				.param("eveningStart", "17:00")
				.param("eveningEnd", "20:00"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/settings"))
			.andExpect(flash().attribute("message", "settingsSaved"));

		ClinicConfig updated = this.settingsService.current();
		assertThat(updated.getBookingHorizonDays()).isEqualTo(45);
		assertThat(updated.getMinDurationMinutes()).isEqualTo(20);
		assertThat(updated.getMaxDurationMinutes()).isEqualTo(90);
		assertThat(updated.getDefaultDurationMinutes()).isEqualTo(45);
		assertThat(updated.getEmergencyPhone()).isEqualTo("555-0999");
		assertThat(updated.getTimeZone()).isEqualTo("Europe/Tallinn");
		assertThat(this.clock.getZone()).isEqualTo(ZoneId.of("Europe/Tallinn"));

		assertThat(this.settingsService.allOpeningHours().stream().map(this::openingHourTuple).toList())
			.containsExactly("MONDAY|08:30|17:30|false", "TUESDAY|08:30|17:30|false", "WEDNESDAY|08:30|17:30|false",
					"THURSDAY|08:30|17:30|false", "FRIDAY|08:30|17:30|false", "SATURDAY|null|null|true",
					"SUNDAY|null|null|true");
		assertThat(this.settingsService.allPartsOfDay().stream().map(this::partOfDayTuple).toList())
			.containsExactly("morning|08:30|12:00", "afternoon|12:00|17:00", "evening|17:00|20:00");

		List<ClinicConfigService.ConfigAuditRecord> auditLog = this.settingsService.getAuditLog();
		assertThat(auditLog).isNotEmpty();
		ClinicConfigService.ConfigAuditRecord lastAudit = auditLog.get(auditLog.size() - 1);
		assertThat(lastAudit.actor()).isEqualTo("staff");
		assertThat(lastAudit.action()).isEqualTo("UPDATE_CLINIC_CONFIG");
	}

	@Test
	@Tag("AC-99")
	void invalidEditPersistsNothing_AC99() throws Exception {
		MockHttpSession staffSession = loginStaff();
		String before = settingsSnapshot();

		// Inverted min/max duration, overlapping / non-contiguous parts of day
		MvcResult result = this.mockMvc
			.perform(post("/staff/settings").session(staffSession)
				.with(csrf())
				.param("horizonDays", "100")
				.param("minDurationMinutes", "60")
				.param("maxDurationMinutes", "15") // inverted! min > max
				.param("defaultDurationMinutes", "30")
				.param("emergencyPhone", "555-0199")
				.param("timeZone", "Europe/Amsterdam")
				.param("morningStart", "12:00")
				.param("morningEnd", "09:00") // inverted!
				.param("afternoonStart", "14:00") // gap! not contiguous with 09:00
				.param("afternoonEnd", "17:00")
				.param("eveningStart", "16:00") // overlapping! 16:00 < 17:00
				.param("eveningEnd", "20:00"))
			.andExpect(status().isOk())
			.andReturn();

		String html = result.getResponse().getContentAsString();
		assertThat(html).contains("class=\"alert alert-danger\"");

		assertThat(settingsSnapshot()).isEqualTo(before);
	}

	private String settingsSnapshot() {
		ClinicConfig config = this.settingsService.current();
		return config.getBookingHorizonDays() + "|" + config.getMinDurationMinutes() + "|"
				+ config.getMaxDurationMinutes() + "|" + config.getDefaultDurationMinutes() + "|"
				+ config.getEmergencyPhone() + "|" + config.getTimeZone() + "|"
				+ this.settingsService.allOpeningHours().stream().map(this::openingHourTuple).toList() + "|"
				+ this.settingsService.allPartsOfDay().stream().map(this::partOfDayTuple).toList();
	}

	private String openingHourTuple(ClinicOpeningHour openingHour) {
		return openingHour.getDayOfWeek() + "|" + openingHour.getOpenTime() + "|" + openingHour.getCloseTime() + "|"
				+ openingHour.isClosed();
	}

	private String partOfDayTuple(ClinicPartOfDay partOfDay) {
		return partOfDay.getName() + "|" + partOfDay.getStartTime() + "|" + partOfDay.getEndTime();
	}

	private MockHttpSession loginStaff() throws Exception {
		MvcResult login = this.mockMvc.perform(formLogin().user("staff").password("staff123"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		return (MockHttpSession) login.getRequest().getSession(false);
	}

}
