package org.springframework.samples.petclinic.scheduling.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffSchedulingWebTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private RequestService requestService;

	@Autowired
	private StaffQueueQueryService queryService;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private SlotSuggestionPort slotSuggestions;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@BeforeEach
	void clearSchedulingData() {
		new TransactionTemplate(this.transactionManager).execute(status -> {
			this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
			this.jdbc.update("delete from visits where appointment_id is not null");
			this.jdbc.update("delete from interpretation_windows");
			this.jdbc.update("delete from interpretation_failures");
			this.jdbc.update("delete from request_rejections");
			this.jdbc.update("delete from appointments");
			this.jdbc.update("delete from interpretations");
			this.jdbc.update("delete from scheduling_requests");
			return null;
		});
	}

	@Test
	void queueContainsExactlyOldestWithStaffAndEveryOtherActiveRequest() throws Exception {
		SchedulingRequest oldest = this.requestService.createForStaff(1, "Oldest staff request").request();
		SchedulingRequest second = this.requestService.createForStaff(2, "Second staff request").request();
		SchedulingRequest inProgress = this.requestService.startForOwner(3, "Awaiting consent request").request();
		SchedulingRequest terminal = this.requestService.createForStaff(4, "Terminal request").request();
		this.requestService.abandon(terminal.getId());

		StaffQueueQueryService.QueueView queue = this.queryService.queue();
		assertThat(queue.needsStaff()).extracting(StaffQueueQueryService.RequestSummary::id)
			.containsExactly(oldest.getId(), second.getId());
		assertThat(queue.inProgress()).extracting(StaffQueueQueryService.RequestSummary::id)
			.containsExactly(inProgress.getId());
		assertThat(queue.needsStaff()).allSatisfy(request -> {
			assertThat(request.reasonMessageKey()).isEqualTo("scheduling.staff.reason.staffCreated");
			assertThat(request.originMessageKey()).isNull();
		});

		String html = rendered(get("/staff/queue").with(user("staff").roles("STAFF")));
		assertThat(html).contains("Needs staff", "In progress", "Oldest staff request", "Awaiting consent",
				"George Franklin", "Leo", "Rosy");
		assertThat(html).doesNotContain("Terminal request");
	}

	@Test
	void detailPrefillsLatestVersionAndPreservesCompleteImmutableHistory() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "Version history request").request();
		Interpretation first = interpretation(InterpretationOrigin.STAFF, CareType.SPECIALTY, "surgery", 75);
		this.requestService.authorInterpretation(request.getId(), request.getVersion(), first);
		long version = currentVersion(request.getId());
		Interpretation second = interpretation(InterpretationOrigin.STAFF, CareType.SPECIALTY, "dentistry", 45);
		this.requestService.authorInterpretation(request.getId(), version, second);

		StaffQueueQueryService.RequestDetail detail = this.queryService.detail(request.getId()).orElseThrow();
		assertThat(detail.current().specialty()).isEqualTo("dentistry");
		assertThat(detail.current().durationMinutes()).isEqualTo(45);
		assertThat(detail.current().preferredWindows()).singleElement()
			.extracting(StaffQueueQueryService.WindowView::weekday)
			.isEqualTo(DayOfWeek.MONDAY);
		assertThat(detail.history()).singleElement()
			.extracting(StaffQueueQueryService.InterpretationView::specialty)
			.isEqualTo("surgery");
		long interpretationCount = count("select count(*) from interpretations where request_id = ?", request.getId());
		StaffInterpretationForm.Values unchanged = new StaffInterpretationForm.Values(CareType.SPECIALTY, "dentistry",
				null, 45, null, List.of(new StaffInterpretationForm.WindowValue("PREFERRED", DayOfWeek.MONDAY, null,
						LocalTime.of(9, 0), LocalTime.of(12, 0))),
				List.of(), List.of());
		this.requestService.authorInterpretation(request.getId(), currentVersion(request.getId()), unchanged);
		assertThat(count("select count(*) from interpretations where request_id = ?", request.getId()))
			.isEqualTo(interpretationCount);

		String html = rendered(
				get("/staff/requests/{id}", request.getId()).with(user("staff").roles("STAFF")).locale(Locale.GERMAN));
		assertThat(html).contains("Version history request", "George Franklin", "Leo", "Clinic staff interpretation",
				"dentistry", "surgery", "Montag", "09:00", "12:00", "value=\"45\"");
		assertThat(html).doesNotContain("WindowView[");
		assertThat(html).contains("/staff/requests/" + request.getId() + "/interpretation",
				"/staff/requests/" + request.getId() + "/suggest", "/staff/requests/" + request.getId() + "/book");
	}

	@Test
	void declinedConsentAfterAnEditStartsEmptyAndKeepsThePriorVersionOnlyAsHistory() throws Exception {
		SchedulingRequest request = this.requestService.startForOwner(1, "Original request text").request();
		this.requestService.consent(request.getId());
		this.requestService.interpretationSucceeded(request.getId(),
				interpretation(InterpretationOrigin.AI, CareType.GENERAL, null, 30));
		this.requestService.editText(request.getId(), "Revised text not shared with the interpreter");
		this.requestService.decline(request.getId());

		StaffQueueQueryService.RequestDetail detail = this.queryService.detail(request.getId()).orElseThrow();
		assertThat(detail.current()).isNull();
		assertThat(detail.history()).singleElement()
			.extracting(StaffQueueQueryService.InterpretationView::origin)
			.isEqualTo(InterpretationOrigin.AI);
		assertThat(count("select count(*) from interpretations where request_id = ?", request.getId())).isOne();

		String html = rendered(get("/staff/requests/{id}", request.getId()).with(user("staff").roles("STAFF")));
		assertThat(html)
			.contains("Owner declined automated interpretation", "Earlier interpretation versions", "AI interpretation",
					"value=\"\"")
			.doesNotContain("/staff/requests/" + request.getId() + "/book",
					"/staff/requests/" + request.getId() + "/suggest");
	}

	@ParameterizedTest(name = "raw duration {1} uses configured staff default {2}")
	@MethodSource("staffDurationBoundaries")
	void rawStaffDurationIsPersistedAndSlotFormsUseConfiguredBoundedDefault(String submittedDuration,
			Integer expectedRawDuration, int expectedStaffDefault) throws Exception {
		this.jdbc.update("""
				update clinic_settings
				set minimum_duration_minutes = 20,
				    default_duration_minutes = 35,
				    maximum_duration_minutes = 50
				""");
		try {
			SchedulingRequest request = this.requestService.createForStaff(1, "Duration boundary").request();

			this.mvc
				.perform(post("/staff/requests/{id}/interpretation", request.getId()).with(user("staff").roles("STAFF"))
					.with(csrf())
					.param("version", Long.toString(request.getVersion()))
					.param("careType", "GENERAL")
					.param("specialty", "")
					.param("specialtyLabel", "")
					.param("durationMinutes", submittedDuration)
					.param("preferredVetId", "")
					.param("preferredWindows", "MONDAY 09:00 12:00")
					.param("allowedWindows", "")
					.param("excludedWindows", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/staff/requests/" + request.getId()));

			Integer storedDuration = this.jdbc.queryForObject(
					"select duration_minutes from interpretations where request_id = ?", Integer.class,
					request.getId());
			assertThat(storedDuration).isEqualTo(expectedRawDuration);

			String html = rendered(get("/staff/requests/{id}", request.getId()).with(user("staff").roles("STAFF")));
			assertInputValue(html, "durationMinutes", submittedDuration);
			assertInputValue(html, "suggest-duration", Integer.toString(expectedStaffDefault));
			assertInputValue(html, "book-duration", Integer.toString(expectedStaffDefault));
		}
		finally {
			this.jdbc.update("""
					update clinic_settings
					set minimum_duration_minutes = 15,
					    default_duration_minutes = 30,
					    maximum_duration_minutes = 60
					""");
		}
	}

	@Test
	void rawDurationAndPreferredVeterinarianHaveIndependentValidation() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "Independent integer validation").request();

		MvcResult result = this.mvc
			.perform(post("/staff/requests/{id}/interpretation", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(request.getVersion()))
				.param("careType", "GENERAL")
				.param("specialty", "")
				.param("specialtyLabel", "")
				.param("durationMinutes", "0")
				.param("preferredVetId", "0")
				.param("preferredWindows", "MONDAY 09:00 12:00")
				.param("allowedWindows", "")
				.param("excludedWindows", ""))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("Choose a valid veterinarian")
			.doesNotContain("Duration must be a whole number");
		assertThat(count("select count(*) from interpretations where request_id = ?", request.getId())).isZero();
	}

	@Test
	void staffConstraintBoundariesReturnSpecificRefusalsAndPreserveExistingCapacity() {
		assertStaffSlotRefusal(1,
				new SlotSuggestionPort.StaffSuggestionCommand(1, LocalDate.of(2026, 9, 8), LocalTime.of(10, 0), 10),
				"scheduling.slot.invalid.duration");
		assertStaffSlotRefusal(2,
				new SlotSuggestionPort.StaffSuggestionCommand(1, LocalDate.of(2026, 9, 8), LocalTime.of(10, 5), 30),
				"scheduling.slot.invalid.grid");
		assertStaffSlotRefusal(3,
				new SlotSuggestionPort.StaffSuggestionCommand(1, LocalDate.of(2026, 9, 12), LocalTime.of(10, 0), 30),
				"scheduling.slot.invalid.opening");
		assertStaffSlotRefusal(4,
				new SlotSuggestionPort.StaffSuggestionCommand(1, LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 30),
				"scheduling.slot.invalid.block");

		SchedulingRequest winner = this.requestService.createForStaff(5, "Boundary winner").request();
		SlotSuggestionPort.StaffSuggestionCommand valid = new SlotSuggestionPort.StaffSuggestionCommand(4,
				LocalDate.of(2026, 9, 10), LocalTime.of(9, 0), 15);
		assertThat(this.slotSuggestions.placeStaffSuggestion(winner, valid, "Boundary winner", "staff")).isTrue();
		SchedulingRequest loser = this.requestService.createForStaff(6, "Overlap loser").request();
		assertThat(this.slotSuggestions.bookDirectly(loser, new SlotSuggestionPort.StaffDirectBookingCommand(4,
				valid.date(), valid.startTime(), valid.durationMinutes(), "Overlapping booking", "staff")))
			.isFalse();
		assertThat(this.appointments.findAll()).singleElement().satisfies(appointment -> {
			assertThat(appointment.getRequest().getId()).isEqualTo(winner.getId());
			assertThat(appointment.getDurationMinutes()).isEqualTo(15);
		});
	}

	@Test
	void incompleteInterpretationIdentifiesFieldsAndCreatesNothing() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "Incomplete interpretation").request();

		MvcResult result = this.mvc
			.perform(post("/staff/requests/{id}/interpretation", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(request.getVersion()))
				.param("careType", "SPECIALTY")
				.param("specialty", "")
				.param("durationMinutes", "not-a-number")
				.param("preferredWindows", "")
				.param("allowedWindows", "")
				.param("excludedWindows", ""))
			.andExpect(status().isOk())
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("Choose a specialty for specialty care",
				"Duration must be a whole number", "Add at least one preferred or allowed window");
		assertThat(count("select count(*) from interpretations where request_id = ?", request.getId())).isZero();
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.WITH_STAFF);
	}

	@Test
	void malformedStaffSlotInputIsLocalizedAndCreatesNothing() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "Malformed slot input").request();
		authorThroughHttp(request.getId(), request.getVersion(), null, "MONDAY 09:00 12:00");

		MvcResult refused = this.mvc
			.perform(post("/staff/requests/{id}/book", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(currentVersion(request.getId())))
				.param("veterinarianId", "not-a-vet")
				.param("date", "not-a-date")
				.param("startTime", "not-a-time")
				.param("durationMinutes", "not-a-duration")
				.param("reason", "Must not be saved"))
			.andExpect(status().is3xxRedirection())
			.andReturn();

		String page = this.mvc
			.perform(get(refused.getResponse().getRedirectedUrl()).with(user("staff").roles("STAFF"))
				.session((MockHttpSession) refused.getRequest().getSession(false)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(page).contains("Choose a valid veterinarian, date, time, and duration");
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(count("select count(*) from appointments where request_id = ?", request.getId())).isZero();
	}

	@Test
	void staffCanOverrideOwnerPreferencesAndSpecialtyButNotClinicConstraints() throws Exception {
		SchedulingRequest bookRequest = this.requestService.createForStaff(1, "Radiology on Monday only").request();
		authorThroughHttp(bookRequest.getId(), bookRequest.getVersion(), "radiology", "MONDAY 09:00 10:00");
		long version = currentVersion(bookRequest.getId());

		this.mvc
			.perform(post("/staff/requests/{id}/book", bookRequest.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(version))
				.param("veterinarianId", "4")
				.param("date", "2026-09-04")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", " "))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requests.findById(bookRequest.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.WITH_STAFF);
		assertThat(count("select count(*) from appointments where request_id = ?", bookRequest.getId())).isZero();

		this.mvc
			.perform(post("/staff/requests/{id}/book", bookRequest.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(version))
				.param("veterinarianId", "4")
				.param("date", "2026-09-04")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Owner and staff agreed on this earlier date"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/staff/requests/" + bookRequest.getId()));

		assertThat(this.requests.findById(bookRequest.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.ACCEPTED);
		assertThat(this.jdbc.queryForMap("""
				select status, vet_id, appointment_date, start_time, last_change_reason, last_changed_by
				from appointments where request_id = ?
				""", bookRequest.getId())).containsEntry("STATUS", "CONFIRMED")
			.containsEntry("VET_ID", 4)
			.containsEntry("LAST_CHANGE_REASON", "Owner and staff agreed on this earlier date")
			.containsEntry("LAST_CHANGED_BY", "staff");
		String ownerAppointments = rendered(get("/my/appointments").with(user("george").roles("OWNER")));
		assertThat(ownerAppointments).contains("2026-09-04", "Rafael Ortega",
				"Owner and staff agreed on this earlier date");

		SchedulingRequest invalid = this.requestService.createForStaff(2, "Closed-day request").request();
		authorThroughHttp(invalid.getId(), invalid.getVersion(), null, "SATURDAY 10:00 12:00");
		long invalidVersion = currentVersion(invalid.getId());
		MvcResult refused = this.mvc
			.perform(post("/staff/requests/{id}/book", invalid.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(invalidVersion))
				.param("veterinarianId", "1")
				.param("date", "2026-09-12")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Requested Saturday"))
			.andExpect(status().is3xxRedirection())
			.andReturn();
		String refusalPage = this.mvc
			.perform(get(refused.getResponse().getRedirectedUrl()).with(user("staff").roles("STAFF"))
				.session((MockHttpSession) refused.getRequest().getSession(false)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		assertThat(refusalPage).contains("outside clinic opening hours");
		assertThat(count("select count(*) from appointments where request_id = ?", invalid.getId())).isZero();
		assertThat(this.requests.findById(invalid.getId()).orElseThrow().getState()).isEqualTo(RequestState.WITH_STAFF);
	}

	@Test
	void staffSuggestionIsOwnerVisibleReasonedAndReleaseIsTheOnlyInProgressMutation() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "Offer a staff slot").request();
		authorThroughHttp(request.getId(), request.getVersion(), "surgery", "THURSDAY 09:00 12:00");
		long version = currentVersion(request.getId());

		this.mvc
			.perform(post("/staff/requests/{id}/suggest", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(version))
				.param("veterinarianId", "4")
				.param("date", "2026-09-10")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", " "))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(count("select count(*) from appointments where request_id = ?", request.getId())).isZero();

		this.mvc
			.perform(post("/staff/requests/{id}/suggest", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(version))
				.param("veterinarianId", "4")
				.param("date", "2026-09-10")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Staff selected after reviewing the request"))
			.andExpect(status().is3xxRedirection());

		assertThat(this.requests.findById(request.getId()).orElseThrow().getState())
			.isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(this.jdbc.queryForMap("""
				select status, last_change_reason, last_changed_by from appointments where request_id = ?
				""", request.getId())).containsEntry("STATUS", "HELD")
			.containsEntry("LAST_CHANGE_REASON", "Staff selected after reviewing the request")
			.containsEntry("LAST_CHANGED_BY", "staff");
		String owner = rendered(get("/my/requests/{id}", request.getId()).with(user("george").roles("OWNER")));
		assertThat(owner).contains("Suggested appointment", "2026-09-10", "Rafael Ortega");

		String queue = rendered(get("/staff/queue").with(user("staff").roles("STAFF")));
		assertThat(queue).contains("In progress", "Held slot", "Hold age", "Release hold")
			.doesNotContain("/staff/requests/" + request.getId() + "/book");
		long offeredVersion = currentVersion(request.getId());
		this.mvc
			.perform(post("/staff/requests/{id}/release-hold", request.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(offeredVersion))
				.param("reason", "Owner called to change the time"))
			.andExpect(status().is3xxRedirection());
		assertThat(this.requests.findById(request.getId()).orElseThrow().getWithStaffReason())
			.isEqualTo(WithStaffReason.HOLD_RELEASED);
		assertThat(count("select count(*) from appointments where request_id = ?", request.getId())).isZero();
	}

	@Test
	void staleAbandonedAndWrongStateActionsHaveNoSideEffects() throws Exception {
		SchedulingRequest stale = this.requestService.createForStaff(1, "Stale action").request();
		long staleVersion = stale.getVersion();
		authorThroughHttp(stale.getId(), staleVersion, null, "MONDAY 09:00 12:00");
		long interpretationsBefore = count("select count(*) from interpretations where request_id = ?", stale.getId());
		this.mvc
			.perform(post("/staff/requests/{id}/book", stale.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(staleVersion))
				.param("veterinarianId", "1")
				.param("date", "2026-09-08")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Stale booking"))
			.andExpect(status().is3xxRedirection());
		assertThat(count("select count(*) from appointments where request_id = ?", stale.getId())).isZero();
		assertThat(count("select count(*) from interpretations where request_id = ?", stale.getId()))
			.isEqualTo(interpretationsBefore);

		SchedulingRequest abandoned = this.requestService.createForStaff(2, "Owner abandons").request();
		authorThroughHttp(abandoned.getId(), abandoned.getVersion(), null, "MONDAY 09:00 12:00");
		long abandonedVersion = currentVersion(abandoned.getId());
		this.requestService.abandon(abandoned.getId());
		long snapshotVersion = currentVersion(abandoned.getId());
		this.mvc
			.perform(post("/staff/requests/{id}/suggest", abandoned.getId()).with(user("staff").roles("STAFF"))
				.with(csrf())
				.param("version", Long.toString(abandonedVersion))
				.param("veterinarianId", "1")
				.param("date", "2026-09-08")
				.param("startTime", "10:00")
				.param("durationMinutes", "30")
				.param("reason", "Too late"))
			.andExpect(status().is3xxRedirection());
		assertThat(currentVersion(abandoned.getId())).isEqualTo(snapshotVersion);
		assertThat(count("select count(*) from appointments where request_id = ?", abandoned.getId())).isZero();
		assertThat(this.queryService.queue().needsStaff()).extracting(StaffQueueQueryService.RequestSummary::id)
			.doesNotContain(abandoned.getId());
		verifyNoInteractions(this.interpretationLauncher);
	}

	private void authorThroughHttp(int requestId, long version, String specialty, String preferredWindows)
			throws Exception {
		var builder = post("/staff/requests/{id}/interpretation", requestId).with(user("staff").roles("STAFF"))
			.with(csrf())
			.param("version", Long.toString(version))
			.param("careType", specialty == null ? "GENERAL" : "SPECIALTY")
			.param("specialty", specialty == null ? "" : specialty)
			.param("specialtyLabel", "")
			.param("durationMinutes", "30")
			.param("preferredVetId", "")
			.param("preferredWindows", preferredWindows)
			.param("allowedWindows", "")
			.param("excludedWindows", "");
		this.mvc.perform(builder).andExpect(status().is3xxRedirection());
	}

	private void assertStaffSlotRefusal(int petId, SlotSuggestionPort.StaffSuggestionCommand command,
			String expectedMessageKey) {
		SchedulingRequest request = this.requestService.createForStaff(petId, "Constraint check " + petId).request();
		assertThatThrownBy(
				() -> this.slotSuggestions.placeStaffSuggestion(request, command, "Constraint check", "staff"))
			.isInstanceOf(StaffSlotUnavailableException.class)
			.hasMessage(expectedMessageKey);
		assertThat(count("select count(*) from appointments where request_id = ?", request.getId())).isZero();
	}

	private void assertInputValue(String html, String id, String value) {
		assertThat(html).containsPattern("<input[^>]*id=\"" + id + "\"[^>]*value=\"" + value + "\"");
	}

	private static Stream<Arguments> staffDurationBoundaries() {
		return Stream.of(Arguments.of("", null, 35), Arguments.of("0", 0, 20), Arguments.of("19", 19, 20),
				Arguments.of("20", 20, 20), Arguments.of("35", 35, 35), Arguments.of("50", 50, 50),
				Arguments.of("51", 51, 50));
	}

	private Interpretation interpretation(InterpretationOrigin origin, CareType careType, String specialty,
			int duration) {
		Interpretation interpretation = new Interpretation(true, careType, specialty, null, duration, null, origin,
				null, null, null, LocalDate.of(2026, 9, 7), LocalTime.of(9, 0));
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		return interpretation;
	}

	private String rendered(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
		return this.mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	private long currentVersion(int requestId) {
		return this.jdbc.queryForObject("select version from scheduling_requests where id = ?", Long.class, requestId);
	}

	private long count(String sql, Object... arguments) {
		return this.jdbc.queryForObject(sql, Long.class, arguments);
	}

}
