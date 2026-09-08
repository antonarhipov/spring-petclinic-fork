package org.springframework.samples.petclinic.scheduling.appointment;

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
import org.springframework.samples.petclinic.scheduling.request.InterpretationLauncher;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SlotSuggestionPort;
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
class AppointmentStateTransitionTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);

	private static final LocalTime NOW = LocalTime.of(10, 0);

	private static final LocalDate FUTURE_DATE = TODAY.plusDays(2);

	private static final Map<AppointmentStatus, Set<AppointmentAction>> ALLOWED = allowedActions();

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private RequestService requestService;

	@Autowired
	private JdbcTemplate jdbc;

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

	@ParameterizedTest(name = "{0}")
	@MethodSource("refusedCases")
	@Tag("AC-119")
	void ac119_every_unlisted_transition_refused(RefusalCase refusal) {
		int appointmentId = createAppointmentIn(refusal.status(), refusal.timing());
		PersistentAppointmentAggregate before = persistentAggregate(appointmentId);
		clearCollaboratorInvocations();

		assertThatThrownBy(() -> refusal.action().invoke(this.appointmentService, appointmentId))
			.isInstanceOf(IllegalAppointmentTransitionException.class);

		assertThat(persistentAggregate(appointmentId)).isEqualTo(before);
		verifyNoMutation();
	}

	@Test
	@Tag("AC-119")
	void ac119_refusal_preserves_appointment_and_visit() {
		int appointmentId = createHeldAppointment();
		PersistentAppointmentAggregate before = persistentAggregate(appointmentId);
		assertThat(before.appointment().get("STATUS")).isEqualTo("HELD");
		assertThat(before.request()).hasSize(1);
		clearCollaboratorInvocations();

		assertThatThrownBy(() -> this.appointmentService.reschedule(appointmentId, 2, FUTURE_DATE.plusDays(1), NOW,
				NOW.plusMinutes(30), "reason", "staff"))
			.isInstanceOf(IllegalAppointmentTransitionException.class);

		assertThat(persistentAggregate(appointmentId)).isEqualTo(before);
		verifyNoMutation();
	}

	private static Stream<Arguments> refusedCases() {
		Stream<Arguments> statusCases = Stream.of(AppointmentStatus.values())
			.flatMap(status -> Stream.of(AppointmentAction.values())
				.filter(action -> !ALLOWED.get(status).contains(action))
				.map(action -> Arguments.of(new RefusalCase(status, Timing.VALID, action))));
		Stream<Arguments> timeCases = Stream.of(
				Arguments.of(new RefusalCase(AppointmentStatus.CONFIRMED, Timing.AFTER_START,
						AppointmentAction.OWNER_CANCEL)),
				Arguments
					.of(new RefusalCase(AppointmentStatus.CONFIRMED, Timing.BEFORE_START, AppointmentAction.COMPLETE)),
				Arguments
					.of(new RefusalCase(AppointmentStatus.CONFIRMED, Timing.BEFORE_START, AppointmentAction.NO_SHOW)));
		return Stream.concat(statusCases, timeCases);
	}

	private static Map<AppointmentStatus, Set<AppointmentAction>> allowedActions() {
		Map<AppointmentStatus, Set<AppointmentAction>> allowed = new EnumMap<>(AppointmentStatus.class);
		allowed.put(AppointmentStatus.HELD, EnumSet.of(AppointmentAction.ACCEPT, AppointmentAction.DELETE_HOLD));
		allowed.put(AppointmentStatus.CONFIRMED,
				EnumSet.of(AppointmentAction.RESCHEDULE, AppointmentAction.OWNER_CANCEL, AppointmentAction.STAFF_CANCEL,
						AppointmentAction.COMPLETE, AppointmentAction.NO_SHOW));
		allowed.put(AppointmentStatus.CANCELLED, EnumSet.noneOf(AppointmentAction.class));
		allowed.put(AppointmentStatus.COMPLETED, EnumSet.noneOf(AppointmentAction.class));
		allowed.put(AppointmentStatus.NO_SHOW, EnumSet.noneOf(AppointmentAction.class));
		return allowed;
	}

	private int createAppointmentIn(AppointmentStatus status, Timing timing) {
		if (status == AppointmentStatus.HELD) {
			return createHeldAppointment();
		}
		Timing effectiveTiming = status == AppointmentStatus.CONFIRMED ? timing : Timing.AFTER_START;
		int appointmentId = createConfirmedAppointment(effectiveTiming);
		if (status == AppointmentStatus.CANCELLED) {
			this.appointmentService.cancelByStaff(appointmentId, "cancelled", "staff");
		}
		else if (status == AppointmentStatus.COMPLETED) {
			this.appointmentService.complete(appointmentId, "completed visit");
		}
		else if (status == AppointmentStatus.NO_SHOW) {
			this.appointmentService.markNoShow(appointmentId);
		}
		return appointmentId;
	}

	private int createHeldAppointment() {
		SchedulingRequest request = this.requestService.createForStaff(1, "held request").request();
		SlotSuggestionPort.StaffSuggestionCommand command = new SlotSuggestionPort.StaffSuggestionCommand(2,
				FUTURE_DATE, NOW, 30);
		when(this.slotSuggestions.placeStaffSuggestion(any(), eq(command), eq("Staff selected slot"), eq("staff")))
			.thenAnswer(invocation -> this.appointmentService
				.createHeld(invocation.getArgument(0), command.veterinarianId(), command.date(), command.startTime(),
						command.startTime().plusMinutes(command.durationMinutes()), "scheduling.rank.staff",
						"Staff selected slot", "staff")
				.isPresent());
		this.requestService.placeStaffSuggestion(request.getId(), request.getVersion(), command, "Staff selected slot",
				"staff");
		return appointmentIdFor(request.getId());
	}

	private int createConfirmedAppointment(Timing timing) {
		SchedulingRequest request = this.requestService.createForStaff(1, "confirmed request").request();
		LocalDate date = timing == Timing.AFTER_START ? TODAY : FUTURE_DATE;
		LocalTime start = timing == Timing.AFTER_START ? NOW.minusHours(1) : NOW;
		SlotSuggestionPort.StaffDirectBookingCommand command = new SlotSuggestionPort.StaffDirectBookingCommand(3, date,
				start, 30, "booking reason", "staff");
		when(this.slotSuggestions.bookDirectly(any(), eq(command))).thenAnswer(invocation -> this.appointmentService
			.bookDirectly(invocation.getArgument(0), command.veterinarianId(), command.date(), command.startTime(),
					command.startTime().plusMinutes(command.durationMinutes()), command.reason(), command.changedBy())
			.isPresent());
		this.requestService.bookDirectly(request.getId(), request.getVersion(), command);
		return appointmentIdFor(request.getId());
	}

	private int appointmentIdFor(int requestId) {
		return this.jdbc.queryForObject("select id from appointments where request_id = ?", Integer.class, requestId);
	}

	private PersistentAppointmentAggregate persistentAggregate(int appointmentId) {
		Map<String, Object> appointment = this.jdbc.queryForMap("""
				select id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status,
				       held_date, held_time, rank_reason, last_change_reason, last_changed_by,
				       cancelled_by, cancelled_date, cancelled_time, created_date, created_time
				from appointments where id = ?
				""", appointmentId);
		List<Map<String, Object>> request = this.jdbc.queryForList("""
				select r.id, r.state, r.active_pet_id, r.version, r.current_interpretation_id,
				       r.failure_count, r.with_staff_reason, r.staff_reason
				from scheduling_requests r join appointments a on a.request_id = r.id where a.id = ?
				""", appointmentId);
		List<Map<String, Object>> visits = this.jdbc.queryForList("""
				select id, pet_id, visit_date, description, appointment_id
				from visits where appointment_id = ? order by id
				""", appointmentId);
		return new PersistentAppointmentAggregate(appointment, request, visits);
	}

	private void clearCollaboratorInvocations() {
		clearInvocations(this.appointments, this.vets, this.interpretationLauncher, this.slotSuggestions);
	}

	private void verifyNoMutation() {
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

	private enum AppointmentAction {

		ACCEPT {
			@Override
			void invoke(AppointmentService service, int id) {
				service.acceptHeld(id);
			}
		},
		DELETE_HOLD {
			@Override
			void invoke(AppointmentService service, int id) {
				service.deleteHeld(id);
			}
		},
		RESCHEDULE {
			@Override
			void invoke(AppointmentService service, int id) {
				service.reschedule(id, 2, FUTURE_DATE.plusDays(1), NOW, NOW.plusMinutes(30), "reason", "staff");
			}
		},
		OWNER_CANCEL {
			@Override
			void invoke(AppointmentService service, int id) {
				service.cancelByOwner(id);
			}
		},
		STAFF_CANCEL {
			@Override
			void invoke(AppointmentService service, int id) {
				service.cancelByStaff(id, "reason", "staff");
			}
		},
		COMPLETE {
			@Override
			void invoke(AppointmentService service, int id) {
				service.complete(id, "description");
			}
		},
		NO_SHOW {
			@Override
			void invoke(AppointmentService service, int id) {
				service.markNoShow(id);
			}
		};

		abstract void invoke(AppointmentService service, int id);

	}

	private enum Timing {

		VALID, BEFORE_START, AFTER_START

	}

	private record RefusalCase(AppointmentStatus status, Timing timing, AppointmentAction action) {

		@Override
		public String toString() {
			return this.status + " " + this.timing + " refuses " + this.action;
		}
	}

	private record PersistentAppointmentAggregate(Map<String, Object> appointment, List<Map<String, Object>> request,
			List<Map<String, Object>> visits) {
	}

}
