package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RequestCreationServiceTests {

	@Autowired
	private RequestService requestService;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@MockitoBean
	private SlotSuggestionPort slotSuggestions;

	@BeforeEach
	void clearRequests() {
		this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
		this.jdbc.update("delete from interpretation_windows");
		this.jdbc.update("delete from interpretation_failures");
		this.jdbc.update("delete from request_rejections");
		this.jdbc.update("delete from appointments");
		this.jdbc.update("delete from interpretations");
		this.jdbc.update("delete from scheduling_requests");
	}

	@Test
	@Tag("AC-26")
	void ac26_valid_text_creates_awaiting_consent() {
		String text = "Leo has been limping since yesterday.";

		RequestService.CreationResult result = this.requestService.startForOwner(1, text);
		SchedulingRequest persisted = this.requests.findById(result.request().getId()).orElseThrow();

		assertThat(result.created()).isTrue();
		assertThat(persisted.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(persisted.getRequestText()).isEqualTo(text);
		assertThat(persisted.getActivePetId()).isEqualTo(1);
	}

	@Test
	@Tag("AC-27")
	void ac27_text_lengths_one_and_2000_are_accepted() {
		assertCreatedWithLength(1, 1);
		assertCreatedWithLength(2, 2);
		assertCreatedWithLength(3, 1999);
		assertCreatedWithLength(4, 2000);

		assertThat(this.requests.count()).isEqualTo(4);
	}

	@Test
	@Tag("AC-28")
	void ac28_blank_and_2001_are_rejected_without_row() {
		long before = this.requests.count();

		assertThat(this.requestService.startForOwner(1, "").messageKey()).isEqualTo("scheduling.request.text.required");
		assertThat(this.requestService.startForOwner(1, "   ").messageKey())
			.isEqualTo("scheduling.request.text.required");
		assertThat(this.requestService.startForOwner(1, "x".repeat(2001)).messageKey())
			.isEqualTo("scheduling.request.text.size");
		assertThat(this.requests.count()).isEqualTo(before);
	}

	@Test
	@Tag("AC-29")
	void ac29_owner_duplicate_returns_existing_request() {
		RequestService.CreationResult first = this.requestService.startForOwner(1, "first request");
		RequestService.CreationResult duplicate = this.requestService.startForOwner(1, "second request");

		assertThat(first.created()).isTrue();
		assertThat(duplicate.created()).isFalse();
		assertThat(duplicate.messageKey()).isNull();
		assertThat(duplicate.request().getId()).isEqualTo(first.request().getId());
		assertThat(this.requests.findAll()).singleElement()
			.extracting(SchedulingRequest::getRequestText)
			.isEqualTo("first request");
	}

	@Test
	@Tag("AC-30")
	void ac30_staff_duplicate_is_refused_without_row() {
		RequestService.CreationResult first = this.requestService.createForStaff(1, "staff-created request");
		RequestService.CreationResult duplicate = this.requestService.createForStaff(1, "duplicate staff request");

		assertThat(first.created()).isTrue();
		assertThat(first.request().getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(first.request().getWithStaffReason()).isEqualTo(WithStaffReason.STAFF_CREATED);
		assertThat(duplicate.request()).isNull();
		assertThat(duplicate.messageKey()).isEqualTo("scheduling.request.duplicate.active");
		assertThat(this.requests.count()).isEqualTo(1);
	}

	@Test
	@Tag("AC-84")
	void ac84_system_and_staff_origins_are_enforced_and_versions_retained() {
		SchedulingRequest ownerRequest = this.requestService.startForOwner(1, "owner request").request();
		this.requestService.consent(ownerRequest.getId());
		SchedulingRequest interpreted = this.requestService.interpretationSucceeded(ownerRequest.getId(),
				interpretation(InterpretationOrigin.AI, "surgery"));
		assertThat(interpreted.getState()).isEqualTo(RequestState.INTERPRETED);

		this.requestService.routeToStaff(ownerRequest.getId());
		long staffVersion = this.requests.findById(ownerRequest.getId()).orElseThrow().getVersion();
		RequestService.ActionResult authored = this.requestService.authorInterpretation(ownerRequest.getId(),
				staffVersion, interpretation(InterpretationOrigin.STAFF, "dentistry"));

		assertThat(authored.request().getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(this.jdbc.queryForList("select origin from interpretations where request_id = ? order by id",
				String.class, ownerRequest.getId()))
			.containsExactly("AI", "STAFF");
		assertThat(this.jdbc.queryForObject("""
				select i.origin from scheduling_requests r
				join interpretations i on i.id = r.current_interpretation_id
				where r.id = ?
				""", String.class, ownerRequest.getId())).isEqualTo("STAFF");

		SchedulingRequest systemOriginGuard = this.requestService.startForOwner(2, "system origin guard").request();
		this.requestService.consent(systemOriginGuard.getId());
		assertThatThrownBy(() -> this.requestService.interpretationSucceeded(systemOriginGuard.getId(),
				interpretation(InterpretationOrigin.STAFF, "surgery")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("system interpretation requires origin AI");
		assertRequestHasStateAndInterpretationCount(systemOriginGuard.getId(), RequestState.INTERPRETING, 0);

		SchedulingRequest staffOriginGuard = this.requestService.createForStaff(3, "staff origin guard").request();
		assertThatThrownBy(() -> this.requestService.authorInterpretation(staffOriginGuard.getId(),
				staffOriginGuard.getVersion(), interpretation(InterpretationOrigin.AI, "surgery")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("staff interpretation requires origin STAFF");
		assertRequestHasStateAndInterpretationCount(staffOriginGuard.getId(), RequestState.WITH_STAFF, 0);
	}

	@Test
	@Tag("AC-86")
	@Tag("AC-87")
	void ac86_ac87_typed_staff_commands_create_result_before_state_change() {
		LocalDate appointmentDate = LocalDate.of(2026, 9, 8);
		LocalTime suggestionStart = LocalTime.of(10, 0);
		SlotSuggestionPort.StaffSuggestionCommand suggestion = new SlotSuggestionPort.StaffSuggestionCommand(2,
				appointmentDate, suggestionStart, 30);
		when(this.slotSuggestions.placeStaffSuggestion(any(), eq(suggestion))).thenAnswer(invocation -> {
			SchedulingRequest request = invocation.getArgument(0);
			return this.appointmentService
				.createHeld(request, suggestion.veterinarianId(), suggestion.date(), suggestion.startTime(),
						suggestion.startTime().plusMinutes(suggestion.durationMinutes()), "scheduling.rank.staff")
				.isPresent();
		});

		SchedulingRequest suggestionRequest = this.requestService.createForStaff(1, "place a suggestion").request();
		RequestService.ActionResult suggested = this.requestService.placeStaffSuggestion(suggestionRequest.getId(),
				suggestionRequest.getVersion(), suggestion);
		assertThat(suggested.request().getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(appointmentSnapshot(suggestionRequest.getId())).isEqualTo(new AppointmentSnapshot(2, appointmentDate,
				suggestionStart, suggestionStart.plusMinutes(30), "HELD", "scheduling.rank.staff", null, null));

		LocalTime bookingStart = LocalTime.of(11, 0);
		SlotSuggestionPort.StaffDirectBookingCommand booking = new SlotSuggestionPort.StaffDirectBookingCommand(3,
				appointmentDate, bookingStart, 30, "Owner requested this time", "staff");
		when(this.slotSuggestions.bookDirectly(any(), eq(booking))).thenAnswer(invocation -> {
			SchedulingRequest request = invocation.getArgument(0);
			return this.appointmentService
				.bookDirectly(request, booking.veterinarianId(), booking.date(), booking.startTime(),
						booking.startTime().plusMinutes(booking.durationMinutes()), booking.reason(),
						booking.changedBy())
				.isPresent();
		});

		SchedulingRequest bookingRequest = this.requestService.createForStaff(2, "book directly").request();
		RequestService.ActionResult booked = this.requestService.bookDirectly(bookingRequest.getId(),
				bookingRequest.getVersion(), booking);
		assertThat(booked.request().getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(booked.request().getActivePetId()).isNull();
		assertThat(appointmentSnapshot(bookingRequest.getId())).isEqualTo(new AppointmentSnapshot(3, appointmentDate,
				bookingStart, bookingStart.plusMinutes(30), "CONFIRMED", null, "Owner requested this time", "staff"));

		assertThatThrownBy(() -> new SlotSuggestionPort.StaffDirectBookingCommand(3, appointmentDate, bookingStart, 30,
				" ", "staff"))
			.isInstanceOf(IllegalArgumentException.class);
		SchedulingRequest refusedRequest = this.requestService.createForStaff(3, "refused booking").request();
		SlotSuggestionPort.StaffDirectBookingCommand refused = new SlotSuggestionPort.StaffDirectBookingCommand(3,
				appointmentDate, LocalTime.of(12, 0), 30, "required reason", "staff");
		when(this.slotSuggestions.bookDirectly(any(), eq(refused))).thenReturn(false);
		assertThatThrownBy(
				() -> this.requestService.bookDirectly(refusedRequest.getId(), refusedRequest.getVersion(), refused))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Slot suggestion port refused the staff-selected booking");
		assertRequestHasStateAndInterpretationCount(refusedRequest.getId(), RequestState.WITH_STAFF, 0);
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where request_id = ?", Integer.class,
				refusedRequest.getId()))
			.isZero();
	}

	private void assertCreatedWithLength(int petId, int length) {
		RequestService.CreationResult result = this.requestService.startForOwner(petId, "x".repeat(length));
		assertThat(result.created()).isTrue();
		assertThat(result.request().getRequestText()).hasSize(length);
	}

	private Interpretation interpretation(InterpretationOrigin origin, String specialty) {
		return new Interpretation(true, CareType.SPECIALTY, specialty, specialty, 30, null, origin, null, null, "test",
				LocalDate.of(2026, 9, 6), LocalTime.of(10, 0));
	}

	private void assertRequestHasStateAndInterpretationCount(int requestId, RequestState state, int count) {
		assertThat(
				this.jdbc.queryForObject("select state from scheduling_requests where id = ?", String.class, requestId))
			.isEqualTo(state.name());
		assertThat(this.jdbc.queryForObject("select count(*) from interpretations where request_id = ?", Integer.class,
				requestId))
			.isEqualTo(count);
	}

	private AppointmentSnapshot appointmentSnapshot(int requestId) {
		return this.jdbc.queryForObject("""
				select vet_id, appointment_date, start_time, end_time, status, rank_reason,
				       last_change_reason, last_changed_by
				from appointments where request_id = ?
				""",
				(result, row) -> new AppointmentSnapshot(result.getInt("vet_id"),
						result.getObject("appointment_date", LocalDate.class),
						result.getObject("start_time", LocalTime.class), result.getObject("end_time", LocalTime.class),
						result.getString("status"), result.getString("rank_reason"),
						result.getString("last_change_reason"), result.getString("last_changed_by")),
				requestId);
	}

	private record AppointmentSnapshot(int veterinarianId, LocalDate date, LocalTime start, LocalTime end,
			String status, String rankReason, String changeReason, String changedBy) {
	}

}
