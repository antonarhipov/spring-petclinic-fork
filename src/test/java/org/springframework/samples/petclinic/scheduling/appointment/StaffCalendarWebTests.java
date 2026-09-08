package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationLauncher;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SlotSuggestionPort;
import org.springframework.samples.petclinic.scheduling.request.StaffSlotUnavailableException;
import org.springframework.samples.petclinic.scheduling.request.WithStaffReason;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffCalendarWebTests {

	private static final LocalDate TUESDAY = LocalDate.of(2026, 9, 8);

	private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 10);

	private static final LocalDate PAST_FRIDAY = LocalDate.of(2026, 9, 4);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private StaffCalendarService calendar;

	@Autowired
	private StaffCalendarQueryService queries;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private RequestService requestService;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@BeforeEach
	void clearSchedulingData() {
		this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
		this.jdbc.update("delete from visits where appointment_id is not null");
		this.jdbc.update("delete from interpretation_windows");
		this.jdbc.update("delete from interpretation_failures");
		this.jdbc.update("delete from request_rejections");
		this.jdbc.update("delete from appointments");
		this.jdbc.update("delete from interpretations");
		this.jdbc.update("delete from scheduling_requests");
	}

	@Test
	void uc5MainAndG1CalendarRendersSixVetsGridAvailabilityConfirmedHeldAndNavigation() throws Exception {
		Appointment confirmed = this.calendar.book(1, 1, TUESDAY, LocalTime.of(9, 0), 30, "Calendar booking", "staff");
		SchedulingRequest request = createInterpretedRequest(2, CareType.GENERAL, null, "Held calendar request");
		this.requestService.placeStaffSuggestion(request.getId(), currentVersion(request.getId()),
				new SlotSuggestionPort.StaffSuggestionCommand(2, TUESDAY, LocalTime.of(10, 0), 30), "Hold for owner",
				"staff");
		int heldId = this.jdbc.queryForObject("select id from appointments where request_id = ?", Integer.class,
				request.getId());

		String page = page(get("/staff/calendar").param("date", TUESDAY.toString()).with(user("staff").roles("STAFF")));

		assertThat(occurrences(page, "<th data-vet-id=\"")).isEqualTo(6);
		assertThat(occurrences(page, "<tr data-time=\"")).isEqualTo(32);
		assertThat(occurrences(page, "<td data-kind=\"")).isEqualTo(32 * 6);
		assertThat(page).contains("09:00-17:00", "James Carter", "Helen Leary", "Linda Douglas", "Rafael Ortega",
				"Henry Stevens", "Sharon Jenkins", "George Franklin", "Leo", "Betty Davis", "Basil");
		assertThat(occurrences(page, "href=\"/staff/appointments/" + confirmed.getId() + "\"")).isOne();
		assertThat(occurrences(page, "href=\"/staff/appointments/" + heldId + "\"")).isOne();
		assertThat(occurrences(page, "data-kind=\"UNAVAILABLE\" data-vet-id=\"6\"")).isEqualTo(32);
		assertThat(page).contains("data-kind=\"FREE\"", "href=\"/staff/calendar?date=2026-09-07\"",
				"href=\"/staff/calendar?date=2026-09-09\"", "id=\"calendar-date\"", "value=\"2026-09-08\"",
				"id=\"booking-duration\"", "value=\"30\"");
		assertThat(this.queries.calendar(LocalDate.of(2026, 9, 12)).clinicClosed()).isTrue();
	}

	@Test
	void uc5MainRescheduleIsImmediateReasonedOwnerVisibleAndSpecialtyMismatchOnlyWarns() throws Exception {
		SchedulingRequest request = createInterpretedRequest(1, CareType.SPECIALTY, "radiology",
				"Radiology appointment");
		this.requestService.bookDirectly(request.getId(), currentVersion(request.getId()),
				new SlotSuggestionPort.StaffDirectBookingCommand(2, TUESDAY, LocalTime.of(10, 0), 30,
						"Initial staff booking", "staff"));
		int appointmentId = appointmentId(request.getId());
		DatabaseSnapshot beforeBlankReason = snapshot();
		assertThatThrownBy(
				() -> this.calendar.reschedule(appointmentId, 4, THURSDAY, LocalTime.of(10, 30), " ", "staff"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(snapshot()).isEqualTo(beforeBlankReason);

		MvcResult response = this.mvc
			.perform(post("/staff/appointments/{id}/reschedule", appointmentId).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("veterinarianId", "4")
				.param("date", THURSDAY.toString())
				.param("startTime", "10:30")
				.param("reason", "Owner called to change the time"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/appointments/" + appointmentId))
			.andReturn();

		String detail = follow(response);
		assertThat(detail).contains("The veterinarian does not have the requested specialty",
				"Owner called to change the time", "Rafael Ortega", "2026-09-10", "10:30-11:00");
		Map<String, Object> row = this.jdbc.queryForMap(
				"select vet_id, appointment_date, start_time, end_time, status, last_change_reason, last_changed_by from appointments where id = ?",
				appointmentId);
		assertThat(row).containsEntry("VET_ID", 4)
			.containsEntry("STATUS", "CONFIRMED")
			.containsEntry("LAST_CHANGE_REASON", "Owner called to change the time")
			.containsEntry("LAST_CHANGED_BY", "staff");
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.ACCEPTED);
		String ownerPage = rendered(get("/my/appointments").with(user("george").roles("OWNER")));
		assertThat(ownerPage).contains("2026-09-10", "10:30-11:00", "Owner called to change the time");

		this.calendar.book(2, 5, THURSDAY, LocalTime.of(11, 0), 30, "Blocking appointment", "staff");
		DatabaseSnapshot beforeConflict = snapshot();
		assertThatThrownBy(
				() -> this.calendar.reschedule(appointmentId, 5, THURSDAY, LocalTime.of(11, 0), "Must lose", "staff"))
			.isInstanceOf(StaffSlotUnavailableException.class);
		assertThat(snapshot()).isEqualTo(beforeConflict);
	}

	@Test
	void uc5Extensions3a3bAnd7aHeldReleaseDirectBookingAndConflictsPreserveState() throws Exception {
		SchedulingRequest heldRequest = createInterpretedRequest(2, CareType.GENERAL, null, "Release this hold");
		this.requestService.placeStaffSuggestion(heldRequest.getId(), currentVersion(heldRequest.getId()),
				new SlotSuggestionPort.StaffSuggestionCommand(2, TUESDAY, LocalTime.of(11, 0), 30), "Hold reason",
				"staff");
		int heldId = appointmentId(heldRequest.getId());
		String heldDetail = page(get("/staff/appointments/{id}", heldId).with(user("staff").roles("STAFF")));
		assertThat(heldDetail).contains("Betty Davis", "Basil", "Release this hold", "Hold age", "0 minutes",
				"href=\"/staff/requests/" + heldRequest.getId() + "\"");

		this.mvc
			.perform(post("/staff/requests/{id}/release-hold", heldRequest.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(currentVersion(heldRequest.getId())))
				.param("reason", "Owner chose another clinic"))
			.andExpect(status().is3xxRedirection());
		assertThat(this.appointments.findById(heldId)).isEmpty();
		SchedulingRequest released = this.requests.findById(heldRequest.getId()).orElseThrow();
		assertThat(released.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(released.getWithStaffReason()).isEqualTo(WithStaffReason.HOLD_RELEASED);
		assertThat(released.getStaffReason()).isEqualTo("Owner chose another clinic");

		MvcResult booked = this.mvc
			.perform(post("/staff/calendar/book").with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("petId", "3")
				.param("veterinarianId", "3")
				.param("date", TUESDAY.toString())
				.param("startTime", "12:00")
				.param("durationMinutes", "30")
				.param("reason", "Booked from calendar capacity"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		int bookedId = this.jdbc.queryForObject("select id from appointments where pet_id = 3 and request_id is null",
				Integer.class);
		assertThat(booked.getResponse().getRedirectedUrl()).isEqualTo("/staff/appointments/" + bookedId);
		assertThat(follow(booked)).contains("Eduardo Rodriquez", "Rosy", "Direct staff booking",
				"Booked from calendar capacity");

		DatabaseSnapshot before = snapshot();
		assertThatThrownBy(() -> this.calendar.book(4, 3, TUESDAY, LocalTime.of(12, 0), 30, "Overlap", "staff"))
			.isInstanceOf(StaffSlotUnavailableException.class);
		assertThatThrownBy(() -> this.calendar.book(4, 4, TUESDAY, LocalTime.of(12, 0), 30, "Unavailable vet", "staff"))
			.isInstanceOf(StaffSlotUnavailableException.class);
		assertThat(snapshot()).isEqualTo(before);
	}

	@Test
	void uc5Extensions5a5d5eCancellationIsAuditedAndPrematureOrFinalChangesAreNoOps() throws Exception {
		Appointment appointment = this.calendar.book(1, 1, TUESDAY, LocalTime.of(14, 0), 30, "Initial booking",
				"staff");
		DatabaseSnapshot beforeBlankReason = snapshot();
		assertThatThrownBy(() -> this.calendar.cancel(appointment.getId(), " ", "staff"))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(snapshot()).isEqualTo(beforeBlankReason);

		this.calendar.cancel(appointment.getId(), "Clinic equipment unavailable", "staff");
		Map<String, Object> cancelled = this.jdbc.queryForMap(
				"select status, cancelled_by, cancelled_date, cancelled_time, last_change_reason, last_changed_by from appointments where id = ?",
				appointment.getId());
		assertThat(cancelled).containsEntry("STATUS", "CANCELLED")
			.containsEntry("CANCELLED_BY", "STAFF")
			.containsEntry("CANCELLED_DATE", java.sql.Date.valueOf(LocalDate.of(2026, 9, 7)))
			.containsEntry("LAST_CHANGE_REASON", "Clinic equipment unavailable")
			.containsEntry("LAST_CHANGED_BY", "staff");
		assertThat(cancelled.get("CANCELLED_TIME").toString()).startsWith("09:00");
		assertThat(rendered(get("/my/appointments").with(user("george").roles("OWNER")))).contains("Cancelled",
				"Clinic equipment unavailable");

		DatabaseSnapshot beforeFinalAction = snapshot();
		assertThatThrownBy(() -> this.calendar.reschedule(appointment.getId(), 2, TUESDAY, LocalTime.of(15, 0),
				"Forged change", "staff"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.calendar.complete(appointment.getId(), "Forged completion"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThat(snapshot()).isEqualTo(beforeFinalAction);

		Appointment future = this.calendar.book(2, 2, TUESDAY, LocalTime.of(15, 0), 30, "Future booking", "staff");
		DatabaseSnapshot beforePremature = snapshot();
		assertThatThrownBy(() -> this.calendar.complete(future.getId(), "Too early"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThatThrownBy(() -> this.calendar.markNoShow(future.getId()))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThat(snapshot()).isEqualTo(beforePremature);
		assertThat(this.queries.detail(future.getId()).orElseThrow().finalizationAvailable()).isFalse();
	}

	@Test
	void uc5Extensions5b5cAndG5CompletionCreatesExactlyOneLinkedVisitWhileNoShowCreatesNone() throws Exception {
		String requestText = "Annual check-up requested by owner: " + "detail ".repeat(50);
		SchedulingRequest request = createInterpretedRequest(1, CareType.GENERAL, null, requestText);
		this.requestService.bookDirectly(request.getId(), currentVersion(request.getId()),
				new SlotSuggestionPort.StaffDirectBookingCommand(5, PAST_FRIDAY, LocalTime.of(11, 0), 30,
						"Past linked booking", "staff"));
		int completedId = appointmentId(request.getId());
		StaffCalendarQueryService.AppointmentDetail detail = this.queries.detail(completedId).orElseThrow();
		assertThat(detail.finalizationAvailable()).isTrue();
		assertThat(detail.completionDescription()).hasSize(255).isEqualTo(requestText.substring(0, 255));

		this.calendar.complete(completedId, detail.completionDescription());
		assertThat(this.jdbc.queryForList(
				"select pet_id, visit_date, description, appointment_id from visits where appointment_id = ?",
				completedId))
			.singleElement()
			.satisfies(row -> assertThat(row).containsEntry("PET_ID", 1)
				.containsEntry("VISIT_DATE", java.sql.Date.valueOf(PAST_FRIDAY))
				.containsEntry("DESCRIPTION", requestText.substring(0, 255))
				.containsEntry("APPOINTMENT_ID", completedId));
		assertThatThrownBy(() -> this.calendar.complete(completedId, "Duplicate visit"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);
		assertThat(this.jdbc.queryForObject("select count(*) from visits where appointment_id = ?", Integer.class,
				completedId))
			.isOne();

		Appointment noShow = this.calendar.book(2, 5, PAST_FRIDAY, LocalTime.of(12, 0), 30, "No-show booking", "staff");
		assertThat(this.queries.detail(noShow.getId()).orElseThrow().completionDescription()).isEmpty();
		this.calendar.markNoShow(noShow.getId());
		assertThat(this.appointments.findById(noShow.getId()).orElseThrow().getStatus())
			.isEqualTo(AppointmentStatus.NO_SHOW);
		assertThat(this.jdbc.queryForObject("select count(*) from visits where appointment_id = ?", Integer.class,
				noShow.getId()))
			.isZero();
		assertThat(rendered(get("/my/appointments").with(user("george").roles("OWNER")))).contains("Completed");
		assertThat(rendered(get("/my/appointments").with(user("betty").roles("OWNER")))).contains("No-show");

		String completedPage = page(get("/staff/appointments/{id}", completedId).with(user("staff").roles("STAFF")));
		assertThat(completedPage).doesNotContain("/reschedule", "/cancel", "/complete", "/no-show");
	}

	private SchedulingRequest createInterpretedRequest(int petId, CareType careType, String specialty, String text) {
		SchedulingRequest request = this.requestService.createForStaff(petId, text).request();
		Interpretation interpretation = new Interpretation(true, careType, specialty, null, 30, null,
				InterpretationOrigin.STAFF, null, null, null, LocalDate.of(2026, 9, 7), LocalTime.of(9, 0));
		interpretation.addWindow(InterpretationWindow.allowed(TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0)));
		this.requestService.authorInterpretation(request.getId(), request.getVersion(), interpretation);
		return this.requests.findById(request.getId()).orElseThrow();
	}

	private long currentVersion(int requestId) {
		return this.jdbc.queryForObject("select version from scheduling_requests where id = ?", Long.class, requestId);
	}

	private int appointmentId(int requestId) {
		return this.jdbc.queryForObject("select id from appointments where request_id = ?", Integer.class, requestId);
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(this.jdbc.queryForList(
				"select id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status, last_change_reason, last_changed_by, cancelled_by, cancelled_date, cancelled_time from appointments order by id"),
				this.jdbc.queryForList(
						"select id, state, active_pet_id, version, with_staff_reason, staff_reason from scheduling_requests order by id"),
				this.jdbc.queryForList(
						"select id, pet_id, visit_date, description, appointment_id from visits where appointment_id is not null order by id"));
	}

	private String page(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private String follow(MvcResult result) throws Exception {
		return page(get(result.getResponse().getRedirectedUrl()).with(user("staff").roles("STAFF"))
			.session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)));
	}

	private String rendered(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
			throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private int occurrences(String value, String needle) {
		return (value.length() - value.replace(needle, "").length()) / needle.length();
	}

	private record DatabaseSnapshot(List<Map<String, Object>> appointments, List<Map<String, Object>> requests,
			List<Map<String, Object>> visits) {
	}

}
