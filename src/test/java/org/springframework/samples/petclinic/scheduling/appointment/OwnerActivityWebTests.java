package org.springframework.samples.petclinic.scheduling.appointment;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OwnerActivityWebTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void uc2MainShowsCompleteReadOnlyOwnerPetAppointmentAndRequestActivity() throws Exception {
		int past = insertAppointment(1, 1, TODAY.minusDays(1), LocalTime.of(10, 0), "COMPLETED", null);
		int upcoming = insertAppointment(1, 3, TODAY.plusDays(1), LocalTime.of(11, 15), "CONFIRMED",
				"Moved for emergency coverage");
		int cancelled = insertAppointment(1, 2, TODAY.plusDays(2), LocalTime.of(13, 0), "CANCELLED", null);
		int requestId = insertInterpretedRequest(1, 2);
		DatabaseSnapshot before = snapshot();

		String pets = render("/my/pets", "george");
		assertThat(pets)
			.contains("data-owner-record", "George", "Franklin", "110 W. Liberty St.", "Madison", "6085551023",
					"data-pet-id=\"1\"", "Leo", "2010-09-07", "cat")
			.doesNotContain("Betty", "Basil", "/owners/1/edit", "/pets/new", "Start a request", "Resume",
					"Cancel appointment");

		String activity = render("/my/appointments", "george");
		assertThat(activity)
			.contains("data-pet-id=\"1\"", "data-appointment-id=\"" + past + "\"",
					"data-appointment-id=\"" + upcoming + "\"", "data-appointment-id=\"" + cancelled + "\"",
					"2026-09-06", "10:00-10:30", "James Carter", "Completed", "2026-09-08", "11:15-11:45",
					"Linda Douglas", "dentistry, surgery", "Confirmed", "Moved for emergency coverage", "2026-09-09",
					"Helen Leary", "Cancelled", "data-active-request", "Ready for confirmation", "AI interpretation",
					"surgery", "radiology", "/my/requests/" + requestId, "Resume",
					"/my/appointments/" + upcoming + "/cancel", "Cancel appointment")
			.doesNotContain("Betty", "Basil", "/vets.html", "Start a request");
		assertThat(occurrences(activity, "data-appointment-action=\"cancel\"")).isOne();

		String requestDetail = render("/my/requests/" + requestId, "george");
		assertThat(requestDetail).contains("AI interpretation", "surgery", "Helen Leary");
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void uc2ExtensionsEmptyOwnerAndPetWithoutActivityShowOnlyValidEmptyActions() throws Exception {
		int emptyOwnerId = insertOwnerWithoutPets();
		String pets = render("/my/pets", "nopets");
		assertThat(pets).contains("My pets", "Owner details", "No pets", "Empty", "Owner", "Nowhere", "0000000000")
			.doesNotContain("data-pet-id", "/my/requests/new", "Start a request", "Resume", "Cancel appointment");
		String emptyActivity = render("/my/appointments", "nopets");
		assertThat(emptyActivity).contains("No pets")
			.doesNotContain("data-pet-id", "/my/requests/new", "Start a request", "Resume", "Cancel appointment");
		assertThat(
				this.jdbc.queryForObject("select count(*) from pets where owner_id = ?", Integer.class, emptyOwnerId))
			.isZero();

		String noActivity = render("/my/appointments", "george");
		assertThat(noActivity)
			.contains("Leo", "No appointments", "No active request", "Start a request", "/my/requests/new?petId=1")
			.doesNotContain("Resume", "Cancel appointment");
	}

	@Test
	void uc2MainOffersCancelOnlyForConfirmedAppointmentsThatHaveNotStarted() throws Exception {
		int eligible = insertAppointment(1, 2, TODAY, LocalTime.of(9, 15), "CONFIRMED", null);
		insertAppointment(1, 2, TODAY, LocalTime.of(9, 0), "CONFIRMED", null);
		insertAppointment(1, 2, TODAY.plusDays(1), LocalTime.of(9, 0), "CANCELLED", null);
		insertAppointment(1, 2, TODAY.minusDays(1), LocalTime.of(9, 0), "COMPLETED", null);
		insertAppointment(1, 2, TODAY.minusDays(2), LocalTime.of(9, 0), "NO_SHOW", null);
		int held = insertAppointment(1, 2, TODAY.plusDays(3), LocalTime.of(9, 0), "HELD", null);

		String activity = render("/my/appointments", "george");
		assertThat(activity).contains("/my/appointments/" + eligible + "/cancel")
			.doesNotContain("data-appointment-id=\"" + held + "\"");
		assertThat(occurrences(activity, "data-appointment-action=\"cancel\"")).isOne();
	}

	@Test
	void uc2ExtensionForeignAndUnknownIdentifiersAreIndistinguishableAndChangeNothing() throws Exception {
		int foreignRequest = insertAwaitingRequest(2, "Betty private request");
		insertAppointment(2, 2, TODAY.plusDays(1), LocalTime.of(14, 0), "CONFIRMED", "Betty private reason");
		DatabaseSnapshot before = snapshot();

		MockHttpServletResponse foreignPet = response("/my/requests/new?petId=2", "george", 404);
		MockHttpServletResponse unknownPet = response("/my/requests/new?petId=999999", "george", 404);
		assertThat(normalizeCsrf(foreignPet.getContentAsString()))
			.isEqualTo(normalizeCsrf(unknownPet.getContentAsString()));

		MockHttpServletResponse foreignRequestResponse = response("/my/requests/" + foreignRequest, "george", 404);
		MockHttpServletResponse unknownRequestResponse = response("/my/requests/999999", "george", 404);
		assertThat(normalizeCsrf(foreignRequestResponse.getContentAsString()))
			.isEqualTo(normalizeCsrf(unknownRequestResponse.getContentAsString()))
			.doesNotContain("Betty private request", "Basil", "Betty");

		String activity = render("/my/appointments", "george");
		assertThat(activity).doesNotContain("Betty private request", "Betty private reason", "Basil", "Betty");
		assertThat(snapshot()).isEqualTo(before);
	}

	private String render(String path, String username) throws Exception {
		return response(path, username, 200).getContentAsString();
	}

	private MockHttpServletResponse response(String path, String username, int expectedStatus) throws Exception {
		return this.mvc.perform(get(path).with(user(username).roles("OWNER")))
			.andExpect(status().is(expectedStatus))
			.andReturn()
			.getResponse();
	}

	private int insertOwnerWithoutPets() {
		this.jdbc.update(
				"insert into owners (first_name, last_name, address, city, telephone) values ('Empty', 'Owner', 'No address', 'Nowhere', '0000000000')");
		int ownerId = this.jdbc.queryForObject("select max(id) from owners", Integer.class);
		this.jdbc.update("""
				insert into user_accounts (username, password_hash, role, owner_id)
				select 'nopets', password_hash, 'OWNER', ? from user_accounts where username = 'george'
				""", ownerId);
		return ownerId;
	}

	private int insertAwaitingRequest(int petId, String text) {
		this.jdbc.update("""
				insert into scheduling_requests
				(pet_id, active_pet_id, request_text, state, failure_count, created_date, created_time, version)
				values (?, ?, ?, 'AWAITING_CONSENT', 0, ?, ?, 0)
				""", petId, petId, text, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 0)));
		return this.jdbc.queryForObject("select max(id) from scheduling_requests", Integer.class);
	}

	private int insertInterpretedRequest(int petId, int preferredVetId) {
		int requestId = insertAwaitingRequest(petId, "Surgery follow-up");
		this.jdbc.update("update scheduling_requests set state = 'INTERPRETED' where id = ?", requestId);
		this.jdbc.update("""
				insert into interpretations
				(request_id, understood, care_type, specialty, duration_minutes, preferred_vet_id, origin,
				 raw_json, model_tag, prompt_version, created_date, created_time)
				values (?, true, 'SPECIALTY', 'surgery', 30, ?, 'AI', '{}', 'deterministic', 'v1', ?, ?)
				""", requestId, preferredVetId, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 1)));
		int interpretationId = this.jdbc.queryForObject("select max(id) from interpretations", Integer.class);
		this.jdbc.update("update scheduling_requests set current_interpretation_id = ? where id = ?", interpretationId,
				requestId);
		return requestId;
	}

	private int insertAppointment(int petId, int vetId, LocalDate date, LocalTime start, String status, String reason) {
		LocalTime end = start.plusMinutes(30);
		LocalDate heldDate = "HELD".equals(status) ? TODAY : null;
		LocalTime heldTime = "HELD".equals(status) ? LocalTime.of(9, 0) : null;
		this.jdbc.update("""
				insert into appointments
				(pet_id, vet_id, appointment_date, start_time, end_time, status, held_date, held_time,
				 last_change_reason, last_changed_by, created_date, created_time)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", petId, vetId, Date.valueOf(date), Time.valueOf(start), Time.valueOf(end), status,
				heldDate == null ? null : Date.valueOf(heldDate), heldTime == null ? null : Time.valueOf(heldTime),
				reason, reason == null ? null : "staff", Date.valueOf(TODAY), Time.valueOf(LocalTime.of(8, 0)));
		return this.jdbc.queryForObject("select max(id) from appointments", Integer.class);
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(this.jdbc.queryForList("select * from owners order by id"),
				this.jdbc.queryForList("select * from pets order by id"),
				this.jdbc.queryForList("select * from scheduling_requests order by id"),
				this.jdbc.queryForList("select * from interpretations order by id"),
				this.jdbc.queryForList("select * from appointments order by id"),
				this.jdbc.queryForList("select * from visits order by id"));
	}

	private String normalizeCsrf(String body) {
		return body.replaceAll("(name=\"_csrf\" value=\")[^\"]+", "$1<token>");
	}

	private int occurrences(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}

	private record DatabaseSnapshot(List<Map<String, Object>> owners, List<Map<String, Object>> pets,
			List<Map<String, Object>> requests, List<Map<String, Object>> interpretations,
			List<Map<String, Object>> appointments, List<Map<String, Object>> visits) {
	}

}
