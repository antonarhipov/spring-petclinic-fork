package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentService;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RequestStateTransitionTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 6);

	private static final LocalTime TIME = LocalTime.of(10, 0);

	private static final LocalDate SLOT_DATE = DATE.plusDays(2);

	private static final Map<RequestState, Set<RequestAction>> ALLOWED = allowedActions();

	@Autowired
	private RequestService requestService;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private JdbcTemplate jdbc;

	@MockitoSpyBean
	private SchedulingRequestRepository requests;

	@MockitoSpyBean
	private InterpretationRepository interpretations;

	@MockitoSpyBean
	private AppointmentRepository appointments;

	@MockitoSpyBean
	private VetRepository vets;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@MockitoBean
	private SlotSuggestionPort slotSuggestions;

	@BeforeEach
	void clearSchedulingData() {
		clearDatabase();
	}

	@Test
	@Tag("AC-92")
	void ac92_stale_request_version_refuses_second_staff_action() {
		SchedulingRequest created = this.requestService.createForStaff(1, "winner text").request();
		long firstLoadedVersion = currentVersion(created.getId());
		long secondLoadedVersion = currentVersion(created.getId());
		assertThat(firstLoadedVersion).isEqualTo(secondLoadedVersion);

		RequestService.ActionResult winner = this.requestService.authorInterpretation(created.getId(),
				firstLoadedVersion, staffInterpretation("surgery"));
		assertThat(winner.messageKey()).isNull();
		PersistentAggregate committedByWinner = persistentAggregate(created.getId());

		clearCollaboratorInvocations();
		RequestService.ActionResult stale = this.requestService.authorInterpretation(created.getId(),
				secondLoadedVersion, staffInterpretation("dentistry"));

		assertThat(stale.messageKey()).isEqualTo("scheduling.request.stale");
		assertThat(persistentAggregate(created.getId())).isEqualTo(committedByWinner);
		verifyNoMutation();
	}

	@ParameterizedTest(name = "{0} refuses {1}")
	@MethodSource("unlistedStateActions")
	@Tag("AC-118")
	void ac118_every_unlisted_transition_refused_by_state(RequestState state, RequestAction action) {
		int requestId = createRequestIn(state);
		long version = currentVersion(requestId);
		PersistentAggregate before = persistentAggregate(requestId);
		clearCollaboratorInvocations();

		assertThatThrownBy(() -> action.invoke(this.requestService, requestId, version))
			.isInstanceOf(IllegalRequestTransitionException.class);

		assertThat(persistentAggregate(requestId)).as("%s after refused %s", state, action).isEqualTo(before);
		verifyNoMutation();
	}

	@Test
	@Tag("AC-118")
	void ac118_refusal_preserves_complete_aggregate() {
		int requestId = createRichSuggestion();
		PersistentAggregate before = persistentAggregate(requestId);
		assertThat(before.interpretations()).hasSize(1);
		assertThat(before.failures()).hasSize(1);
		assertThat(before.appointments()).hasSize(1);
		clearCollaboratorInvocations();

		assertThatThrownBy(() -> this.requestService.consent(requestId))
			.isInstanceOf(IllegalRequestTransitionException.class);

		assertThat(persistentAggregate(requestId)).isEqualTo(before);
		verifyNoMutation();
	}

	@Test
	@Tag("AC-118")
	void ac118_late_result_outside_interpreting_is_noop() {
		for (RequestState state : RequestState.values()) {
			if (state == RequestState.INTERPRETING) {
				continue;
			}
			clearDatabase();
			int requestId = createRequestIn(state);
			PersistentAggregate before = persistentAggregate(requestId);
			clearCollaboratorInvocations();

			this.requestService.interpretationSucceeded(requestId, aiInterpretation("surgery"));
			this.requestService.interpretationFailed(requestId, failure());

			assertThat(persistentAggregate(requestId)).as("late results in %s", state).isEqualTo(before);
			verifyNoMutation();
		}
	}

	private static Stream<Arguments> unlistedStateActions() {
		return Stream.of(RequestState.values())
			.flatMap(state -> Stream.of(RequestAction.values())
				.filter(action -> !action.lateResult())
				.filter(action -> !ALLOWED.get(state).contains(action))
				.map(action -> Arguments.of(state, action)));
	}

	private static Map<RequestState, Set<RequestAction>> allowedActions() {
		Map<RequestState, Set<RequestAction>> allowed = new EnumMap<>(RequestState.class);
		allowed.put(RequestState.AWAITING_CONSENT, EnumSet.of(RequestAction.CONSENT, RequestAction.DECLINE,
				RequestAction.EDIT_TEXT, RequestAction.ABANDON));
		allowed.put(RequestState.INTERPRETING, EnumSet.of(RequestAction.INTERPRETATION_SUCCEEDED,
				RequestAction.INTERPRETATION_FAILED, RequestAction.AI_UNAVAILABLE, RequestAction.ABANDON));
		allowed.put(RequestState.INTERPRETATION_FAILED,
				EnumSet.of(RequestAction.EDIT_TEXT, RequestAction.ROUTE_TO_STAFF, RequestAction.ABANDON));
		allowed.put(RequestState.INTERPRETED, EnumSet.of(RequestAction.CONFIRM, RequestAction.EDIT_TEXT,
				RequestAction.ROUTE_TO_STAFF, RequestAction.ABANDON));
		allowed.put(RequestState.SUGGESTION_OFFERED,
				EnumSet.of(RequestAction.ACCEPT, RequestAction.ANOTHER_OPTION, RequestAction.EDIT_TEXT,
						RequestAction.ROUTE_TO_STAFF, RequestAction.RELEASE_HOLD, RequestAction.SCHEDULE_CHANGED,
						RequestAction.ABANDON));
		allowed.put(RequestState.WITH_STAFF, EnumSet.of(RequestAction.AUTHOR_INTERPRETATION,
				RequestAction.STAFF_SUGGEST, RequestAction.STAFF_BOOK, RequestAction.ABANDON));
		allowed.put(RequestState.ACCEPTED, EnumSet.noneOf(RequestAction.class));
		allowed.put(RequestState.ABANDONED, EnumSet.noneOf(RequestAction.class));
		return allowed;
	}

	private int createRequestIn(RequestState state) {
		return switch (state) {
			case AWAITING_CONSENT -> createOwnerRequest();
			case INTERPRETING -> {
				int id = createOwnerRequest();
				this.requestService.consent(id);
				yield id;
			}
			case INTERPRETATION_FAILED -> {
				int id = createRequestIn(RequestState.INTERPRETING);
				this.requestService.interpretationFailed(id, failure());
				yield id;
			}
			case INTERPRETED -> {
				int id = createRequestIn(RequestState.INTERPRETING);
				this.requestService.interpretationSucceeded(id, aiInterpretation("surgery"));
				yield id;
			}
			case WITH_STAFF -> this.requestService.createForStaff(1, "staff request").request().getId();
			case SUGGESTION_OFFERED -> createStaffSuggestion();
			case ACCEPTED -> createAcceptedRequest();
			case ABANDONED -> {
				int id = createOwnerRequest();
				this.requestService.abandon(id);
				yield id;
			}
		};
	}

	private int createOwnerRequest() {
		return this.requestService.startForOwner(1, "owner request").request().getId();
	}

	private int createStaffSuggestion() {
		SchedulingRequest request = this.requestService.createForStaff(1, "staff suggestion").request();
		SlotSuggestionPort.StaffSuggestionCommand command = suggestionCommand();
		when(this.slotSuggestions.placeStaffSuggestion(any(), eq(command), eq("Staff selected slot"), eq("staff")))
			.thenAnswer(invocation -> this.appointmentService
				.createHeld(invocation.getArgument(0), command.veterinarianId(), command.date(), command.startTime(),
						command.startTime().plusMinutes(command.durationMinutes()), "scheduling.rank.staff",
						"Staff selected slot", "staff")
				.isPresent());
		this.requestService.placeStaffSuggestion(request.getId(), request.getVersion(), command, "Staff selected slot",
				"staff");
		return request.getId();
	}

	private int createAcceptedRequest() {
		SchedulingRequest request = this.requestService.createForStaff(1, "staff booking").request();
		SlotSuggestionPort.StaffDirectBookingCommand command = bookingCommand();
		when(this.slotSuggestions.bookDirectly(any(), eq(command))).thenAnswer(invocation -> this.appointmentService
			.bookDirectly(invocation.getArgument(0), command.veterinarianId(), command.date(), command.startTime(),
					command.startTime().plusMinutes(command.durationMinutes()), command.reason(), command.changedBy())
			.isPresent());
		this.requestService.bookDirectly(request.getId(), request.getVersion(), command);
		return request.getId();
	}

	private int createRichSuggestion() {
		int requestId = createRequestIn(RequestState.INTERPRETING);
		this.requestService.interpretationFailed(requestId, failure());
		this.requestService.editText(requestId, "edited after failure");
		this.requestService.consent(requestId);
		this.requestService.interpretationSucceeded(requestId, aiInterpretation("surgery"));
		when(this.slotSuggestions.placeSuggestion(any())).thenAnswer(
				invocation -> this.appointmentService
					.createHeld(invocation.getArgument(0), 2, SLOT_DATE, TIME, TIME.plusMinutes(30),
							"scheduling.rank.preferred")
					.isPresent());
		this.requestService.confirmInterpretation(requestId);
		return requestId;
	}

	private SlotSuggestionPort.StaffSuggestionCommand suggestionCommand() {
		return new SlotSuggestionPort.StaffSuggestionCommand(2, SLOT_DATE, TIME, 30);
	}

	private SlotSuggestionPort.StaffDirectBookingCommand bookingCommand() {
		return new SlotSuggestionPort.StaffDirectBookingCommand(3, SLOT_DATE, TIME, 30, "staff reason", "staff");
	}

	private Interpretation aiInterpretation(String specialty) {
		return interpretation(InterpretationOrigin.AI, specialty);
	}

	private Interpretation staffInterpretation(String specialty) {
		return interpretation(InterpretationOrigin.STAFF, specialty);
	}

	private Interpretation interpretation(InterpretationOrigin origin, String specialty) {
		return new Interpretation(true, CareType.SPECIALTY, specialty, specialty, 30, null, origin, null, null, "test",
				DATE, TIME);
	}

	private InterpretationFailure failure() {
		return new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "raw", DATE, TIME);
	}

	private long currentVersion(int requestId) {
		return this.jdbc.queryForObject("select version from scheduling_requests where id = ?", Long.class, requestId);
	}

	private PersistentAggregate persistentAggregate(int requestId) {
		Map<String, Object> request = this.jdbc.queryForMap("""
				select id, pet_id, request_text, state, failure_count, with_staff_reason, staff_reason,
				       active_pet_id, current_interpretation_id, created_date, created_time,
				       updated_date, updated_time, version
				from scheduling_requests where id = ?
				""", requestId);
		List<Map<String, Object>> versionRows = this.jdbc.queryForList("""
				select id, request_id, understood, care_type, specialty, specialty_label, duration_minutes,
				       preferred_vet_id, origin, raw_json, model_tag, prompt_version, created_date, created_time
				from interpretations where request_id = ? order by id
				""", requestId);
		List<Map<String, Object>> windows = this.jdbc.queryForList("""
				select w.id, w.interpretation_id, w.window_kind, w.weekday, w.window_date, w.start_time, w.end_time
				from interpretation_windows w join interpretations i on i.id = w.interpretation_id
				where i.request_id = ? order by w.id
				""", requestId);
		List<Map<String, Object>> failures = this.jdbc.queryForList("""
				select id, request_id, failure_kind, raw_output, created_date, created_time
				from interpretation_failures where request_id = ? order by id
				""", requestId);
		List<Map<String, Object>> rejections = this.jdbc.queryForList("""
				select id, request_id, vet_id, appointment_date, start_time
				from request_rejections where request_id = ? order by id
				""", requestId);
		List<Map<String, Object>> appointmentRows = this.jdbc.queryForList("""
				select id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status,
				       held_date, held_time, rank_reason, last_change_reason, last_changed_by,
				       cancelled_by, cancelled_date, cancelled_time, created_date, created_time
				from appointments where request_id = ? order by id
				""", requestId);
		return new PersistentAggregate(request, versionRows, windows, failures, rejections, appointmentRows);
	}

	private void clearCollaboratorInvocations() {
		clearInvocations(this.requests, this.interpretations, this.appointments, this.vets, this.interpretationLauncher,
				this.slotSuggestions);
	}

	private void verifyNoMutation() {
		verify(this.requests, never()).save(any());
		verify(this.requests, never()).saveAndFlush(any());
		verify(this.interpretations, never()).save(any());
		verify(this.appointments, never()).save(any());
		verify(this.appointments, never()).saveAndFlush(any());
		verify(this.appointments, never()).delete(any());
		verify(this.vets, never()).findByIdForUpdate(anyInt());
		verifyNoInteractions(this.interpretationLauncher, this.slotSuggestions);
	}

	private void clearDatabase() {
		this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
		this.jdbc.update("delete from visits where appointment_id is not null");
		this.jdbc.update("delete from interpretation_windows");
		this.jdbc.update("delete from interpretation_failures");
		this.jdbc.update("delete from request_rejections");
		this.jdbc.update("delete from appointments");
		this.jdbc.update("delete from interpretations");
		this.jdbc.update("delete from scheduling_requests");
	}

	private enum RequestAction {

		CONSENT {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.consent(id);
			}
		},
		DECLINE {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.decline(id);
			}
		},
		EDIT_TEXT {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.editText(id, "edited");
			}
		},
		INTERPRETATION_SUCCEEDED(true) {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.interpretationSucceeded(id, aiInterpretationForAction());
			}
		},
		INTERPRETATION_FAILED(true) {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.interpretationFailed(id,
						new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "raw", DATE, TIME));
			}
		},
		AI_UNAVAILABLE {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.aiUnavailable(id);
			}
		},
		ROUTE_TO_STAFF {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.routeToStaff(id);
			}
		},
		CONFIRM {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.confirmInterpretation(id);
			}
		},
		ACCEPT {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.acceptSuggestion(id);
			}
		},
		ANOTHER_OPTION {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.anotherSuggestion(id);
			}
		},
		RELEASE_HOLD {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.releaseHold(id, version, "reason");
			}
		},
		SCHEDULE_CHANGED {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.scheduleChanged(id);
			}
		},
		AUTHOR_INTERPRETATION {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.authorInterpretation(id, version, staffInterpretationForAction());
			}
		},
		STAFF_SUGGEST {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.placeStaffSuggestion(id, version,
						new SlotSuggestionPort.StaffSuggestionCommand(2, SLOT_DATE, TIME, 30), "Staff selected slot",
						"staff");
			}
		},
		STAFF_BOOK {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.bookDirectly(id, version, new SlotSuggestionPort.StaffDirectBookingCommand(3, SLOT_DATE, TIME,
						30, "staff reason", "staff"));
			}
		},
		ABANDON {
			@Override
			void invoke(RequestService service, int id, long version) {
				service.abandon(id);
			}
		};

		private final boolean lateResult;

		RequestAction() {
			this(false);
		}

		RequestAction(boolean lateResult) {
			this.lateResult = lateResult;
		}

		boolean lateResult() {
			return this.lateResult;
		}

		abstract void invoke(RequestService service, int id, long version);

		private static Interpretation aiInterpretationForAction() {
			return new Interpretation(true, CareType.SPECIALTY, "surgery", "surgery", 30, null, InterpretationOrigin.AI,
					null, null, "test", DATE, TIME);
		}

		private static Interpretation staffInterpretationForAction() {
			return new Interpretation(true, CareType.SPECIALTY, "surgery", "surgery", 30, null,
					InterpretationOrigin.STAFF, null, null, "test", DATE, TIME);
		}

	}

	private record PersistentAggregate(Map<String, Object> request, List<Map<String, Object>> interpretations,
			List<Map<String, Object>> windows, List<Map<String, Object>> failures, List<Map<String, Object>> rejections,
			List<Map<String, Object>> appointments) {
	}

}
