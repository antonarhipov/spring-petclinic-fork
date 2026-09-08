package org.springframework.samples.petclinic.scheduling.config;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClinicConfigurationWebTests {

	private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 10);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ClinicConfigurationService configurations;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void uc7MainStaffSeesAndSavesEveryConfigurationTypeWithNoAffectedRequests() throws Exception {
		String page = this.mvc.perform(get("/staff/settings").with(staff()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(page)
			.contains("Clinic settings", "Scheduling limits", "Europe/Amsterdam", "Monday", "Sunday",
					"Recurring working blocks", "1,MONDAY,09:00,17:00", "Date exceptions", "1,2026-09-15",
					"Veterinarian leave", "Clinic closures", "James Carter", "Sharon Jenkins", "name=\"_csrf\"",
					"Derived from each weekday&#39;s opening hours", ">staff</span>", ">Logout</button>")
			.doesNotContain("name=\"morningStart\"", "name=\"eveningEnd\"");

		ClinicConfigurationForm form = ClinicConfigurationForm.from(this.configurations.current());
		form.setBookingHorizonDays(40);
		form.setMinimumLeadDays(2);
		form.setMinimumDurationMinutes(20);
		form.setDefaultDurationMinutes(40);
		form.setMaximumDurationMinutes(80);
		form.setTimeZone("Europe/Paris");
		form.setLeavePeriods("2,2026-09-28,2026-09-29");
		form.setClosures("2026-12-25");

		this.mvc.perform(request(form))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers
				.containsString("The configuration was saved. No held suggestions were affected.")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("No scheduling requests were affected.")));
		assertThat(this.jdbc.queryForMap("select * from clinic_settings")).containsEntry("BOOKING_HORIZON_DAYS", 40)
			.containsEntry("MINIMUM_LEAD_DAYS", 2)
			.containsEntry("MINIMUM_DURATION_MINUTES", 20)
			.containsEntry("DEFAULT_DURATION_MINUTES", 40)
			.containsEntry("MAXIMUM_DURATION_MINUTES", 80)
			.containsEntry("TIME_ZONE", "Europe/Paris");
		assertThat(this.jdbc.queryForList("select * from vet_leave")).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("VET_ID", 2)
				.containsEntry("START_DATE", Date.valueOf(LocalDate.of(2026, 9, 28)))
				.containsEntry("END_DATE", Date.valueOf(LocalDate.of(2026, 9, 29))));
		assertThat(this.jdbc.queryForList("select * from clinic_closures")).singleElement()
			.satisfies(row -> assertThat(row).containsEntry("CLOSURE_DATE", Date.valueOf(LocalDate.of(2026, 12, 25))));
	}

	@Test
	void uc7Extension3aMalformedAndOverlappingValuesExplainFailureAndSaveNothing() throws Exception {
		ClinicConfigurationForm malformed = ClinicConfigurationForm.from(this.configurations.current());
		malformed.setWorkingPeriods("1,MONDAY,9:00,12:00");
		Map<String, Object> before = this.jdbc.queryForMap("select * from clinic_settings");
		this.mvc.perform(request(malformed))
			.andExpect(status().isOk())
			.andExpect(
					content().string(org.hamcrest.Matchers.containsString("Times must use the HH:mm 24-hour format.")));
		assertThat(this.jdbc.queryForMap("select * from clinic_settings")).isEqualTo(before);
		assertThat(this.jdbc.queryForObject("select count(*) from vet_working_blocks", Integer.class)).isEqualTo(17);

		ClinicConfigurationForm overlap = ClinicConfigurationForm.from(this.configurations.current());
		overlap.setWorkingPeriods(overlap.getWorkingPeriods() + "\n1,MONDAY,10:00,11:00");
		this.mvc.perform(request(overlap))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers
				.containsString("Working blocks for one veterinarian and weekday must not overlap.")));
		assertThat(this.jdbc.queryForMap("select * from clinic_settings")).isEqualTo(before);
		assertThat(this.jdbc.queryForObject("select count(*) from vet_working_blocks", Integer.class)).isEqualTo(17);
	}

	@Test
	void uc7Extension4aListsEveryConfirmedConflictAndPreservesConfigurationAndHolds() throws Exception {
		int firstConfirmed = insertAppointment(null, 1, 4, LocalTime.of(9, 0), "CONFIRMED");
		int secondConfirmed = insertAppointment(null, 2, 4, LocalTime.of(10, 0), "CONFIRMED");
		int requestId = insertRequest(3);
		int heldId = insertAppointment(requestId, 3, 4, LocalTime.of(11, 0), "HELD");
		ClinicConfigurationForm form = ClinicConfigurationForm.from(this.configurations.current());
		form.setClosures(THURSDAY.toString());

		String page = this.mvc.perform(request(form))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(page).contains("The configuration was not saved", "Conflicting confirmed appointments",
				"Reschedule every listed appointment", "/staff/appointments/" + firstConfirmed,
				"/staff/appointments/" + secondConfirmed, "No holds were released.");
		assertThat(this.jdbc.queryForObject("select count(*) from clinic_closures", Integer.class)).isZero();
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where id = ?", Integer.class, heldId))
			.isOne();
		assertThat(this.jdbc.queryForMap("select state, with_staff_reason from scheduling_requests where id = ?",
				requestId))
			.containsEntry("STATE", "SUGGESTION_OFFERED")
			.containsEntry("WITH_STAFF_REASON", null);
	}

	@Test
	void uc7MainHoldConflictsAreReportedAfterAtomicSave() throws Exception {
		int requestId = insertRequest(1);
		int heldId = insertAppointment(requestId, 1, 4, LocalTime.of(9, 0), "HELD");
		ClinicConfigurationForm form = ClinicConfigurationForm.from(this.configurations.current());
		form.setClosures(THURSDAY.toString());

		String page = this.mvc.perform(request(form))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(page).contains("The configuration was saved", "Affected scheduling requests",
				"/staff/requests/" + requestId, "Leo", "Rafael Ortega");
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where id = ?", Integer.class, heldId))
			.isZero();
		assertThat(this.jdbc.queryForMap("select state, with_staff_reason from scheduling_requests where id = ?",
				requestId))
			.containsEntry("STATE", "WITH_STAFF")
			.containsEntry("WITH_STAFF_REASON", "SCHEDULE_CHANGED");
	}

	private MockHttpServletRequestBuilder request(ClinicConfigurationForm form) {
		MockHttpServletRequestBuilder request = post("/staff/settings").with(staff())
			.with(csrf())
			.param("bookingHorizonDays", form.getBookingHorizonDays().toString())
			.param("minimumLeadDays", form.getMinimumLeadDays().toString())
			.param("minimumDurationMinutes", form.getMinimumDurationMinutes().toString())
			.param("defaultDurationMinutes", form.getDefaultDurationMinutes().toString())
			.param("maximumDurationMinutes", form.getMaximumDurationMinutes().toString())
			.param("timeZone", form.getTimeZone())
			.param("workingPeriods", form.getWorkingPeriods())
			.param("exceptions", form.getExceptions())
			.param("leavePeriods", form.getLeavePeriods())
			.param("closures", form.getClosures());
		for (int index = 0; index < form.getOpeningHours().size(); index++) {
			ClinicConfigurationForm.OpeningHoursValue hours = form.getOpeningHours().get(index);
			request.param("openingHours[" + index + "].weekday", hours.getWeekday().name())
				.param("openingHours[" + index + "].closed", Boolean.toString(hours.isClosed()))
				.param("openingHours[" + index + "].openTime", hours.getOpenTime())
				.param("openingHours[" + index + "].closeTime", hours.getCloseTime());
		}
		return request;
	}

	private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor staff() {
		return user("staff").roles("STAFF");
	}

	private int insertRequest(int petId) {
		int id = nextId("scheduling_requests");
		this.jdbc.update("""
				insert into scheduling_requests
				(id, pet_id, active_pet_id, request_text, state, created_date, created_time, failure_count, version)
				values (?, ?, ?, 'Configuration web fixture', 'SUGGESTION_OFFERED', '2026-09-07', '09:00:00', 0, 0)
				""", id, petId, petId);
		return id;
	}

	private int insertAppointment(Integer requestId, int petId, int veterinarianId, LocalTime start, String status) {
		int id = nextId("appointments");
		this.jdbc.update("""
				insert into appointments
				(id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status,
				 held_date, held_time, created_date, created_time)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '2026-09-07', '09:00:00')
				""", id, requestId, petId, veterinarianId, Date.valueOf(THURSDAY), Time.valueOf(start),
				Time.valueOf(start.plusMinutes(30)), status,
				"HELD".equals(status) ? Date.valueOf(LocalDate.of(2026, 9, 7)) : null,
				"HELD".equals(status) ? Time.valueOf(LocalTime.of(9, 0)) : null);
		return id;
	}

	private int nextId(String table) {
		return this.jdbc.queryForObject("select coalesce(max(id), 0) + 1 from " + table, Integer.class);
	}

}
