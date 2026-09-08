package org.springframework.samples.petclinic.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.StaffCalendarService;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.CareType;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SlotSuggestionPort;
import org.springframework.samples.petclinic.scheduling.request.StaffSlotUnavailableException;
import org.springframework.samples.petclinic.scheduling.support.TestClockConfiguration;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
class ConcurrencyInvariantTests {

	@Autowired
	private RequestService requestService;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentService appointmentService;

	@Autowired
	private StaffCalendarService staffCalendarService;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clearSchedulingRows() {
		this.jdbcTemplate.update("update scheduling_requests set current_interpretation_id = null");
		this.jdbcTemplate.update("delete from interpretation_windows");
		this.jdbcTemplate.update("delete from interpretation_failures");
		this.jdbcTemplate.update("delete from request_rejections");
		this.jdbcTemplate.update("delete from appointments");
		this.jdbcTemplate.update("delete from interpretations");
		this.jdbcTemplate.update("delete from scheduling_requests");
	}

	@Test
	void uc4ConcurrentStaffCreationsReturnTheSingleExistingRequest() throws Exception {
		List<RequestService.CreationResult> results = race(
				() -> this.requestService.createForStaff(1, "Staff caller one"),
				() -> this.requestService.createForStaff(1, "Staff caller two"));

		assertThat(results).extracting(result -> result.request().getId())
			.doesNotContainNull()
			.containsOnly(results.get(0).request().getId());
		assertThat(results).filteredOn(RequestService.CreationResult::created).hasSize(1);
		assertThat(this.requests.findAll()).singleElement().satisfies(request -> {
			assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(request.getRequestText()).isIn("Staff caller one", "Staff caller two");
		});
	}

	@Test
	void uc4ConcurrentStaffSlotClaimsHaveOneWinnerAndOneUnchangedRequest() throws Exception {
		SchedulingRequest first = this.requestService.createForStaff(1, "First staff slot claim").request();
		SchedulingRequest second = this.requestService.createForStaff(2, "Second staff slot claim").request();
		SlotSuggestionPort.StaffSuggestionCommand command = new SlotSuggestionPort.StaffSuggestionCommand(4,
				LocalDate.of(2026, 9, 10), LocalTime.of(10, 0), 30);

		List<String> results = race(() -> staffSuggestionOutcome(first, command),
				() -> staffSuggestionOutcome(second, command));

		assertThat(results).containsExactlyInAnyOrder("COMPLETED", "UNAVAILABLE");
		assertThat(this.requests.findAll()).extracting(SchedulingRequest::getState)
			.containsExactlyInAnyOrder(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		assertThat(this.appointments.findAll()).singleElement().satisfies(appointment -> {
			assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.HELD);
			assertThat(appointment.getLastChangeReason()).isEqualTo("Concurrent staff selection");
			assertThat(appointment.getLastChangedBy()).isEqualTo("staff");
		});
	}

	@Test
	void uc4ConcurrentActionsOnOneRequestUseTheOptimisticVersionBeforeSideEffects() throws Exception {
		SchedulingRequest request = this.requestService.createForStaff(1, "One request, two staff actions").request();
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.STAFF, null, null, null, LocalDate.of(2026, 9, 7), LocalTime.of(9, 0));
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.authorInterpretation(request.getId(), request.getVersion(), interpretation);
		long expectedVersion = this.requests.findById(request.getId()).orElseThrow().getVersion();

		List<RequestService.ActionResult> results = race(
				() -> this.requestService.bookDirectly(request.getId(), expectedVersion,
						new SlotSuggestionPort.StaffDirectBookingCommand(4, LocalDate.of(2026, 9, 10),
								LocalTime.of(9, 0), 30, "First concurrent booking", "staff")),
				() -> this.requestService.bookDirectly(request.getId(), expectedVersion,
						new SlotSuggestionPort.StaffDirectBookingCommand(5, LocalDate.of(2026, 9, 10),
								LocalTime.of(9, 0), 30, "Second concurrent booking", "staff")));

		assertThat(results).extracting(RequestService.ActionResult::messageKey)
			.containsExactlyInAnyOrder(null, "scheduling.request.stale");
		assertThat(this.requests.findById(request.getId()).orElseThrow().getState()).isEqualTo(RequestState.ACCEPTED);
		assertThat(this.appointments.findAll()).singleElement().satisfies(appointment -> {
			assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
			assertThat(appointment.getLastChangeReason()).isIn("First concurrent booking", "Second concurrent booking");
		});
	}

	@Test
	@Tag("AC-31")
	void ac31_two_request_creations_leave_one_active_row() throws Exception {
		String firstText = "Annual check-up from caller one";
		String secondText = "Annual check-up from caller two";
		List<SchedulingRequest> results = race(() -> this.requestService.createForOwner(1, firstText),
				() -> this.requestService.createForOwner(1, secondText));

		assertThat(results).extracting(SchedulingRequest::getId)
			.doesNotContainNull()
			.containsOnly(results.get(0).getId());
		assertThat(this.requests.findAll()).singleElement().satisfies(request -> {
			assertThat(request.getId()).isEqualTo(results.get(0).getId());
			assertThat(request.getActivePetId()).isEqualTo(1);
			assertThat(request.getRequestText()).isIn(firstText, secondText);
		});
		assertThat(this.requests.countByActivePetIdIsNotNull()).isEqualTo(1);
		assertThat(this.jdbcTemplate.queryForList("""
				select column_name from information_schema.key_column_usage
				where table_name = 'SCHEDULING_REQUESTS'
				and constraint_name = 'UQ_SCHEDULING_REQUESTS_ACTIVE_PET'
				order by ordinal_position
				""", String.class)).containsExactly("ACTIVE_PET_ID");
	}

	@Test
	@Tag("AC-79")
	void ac79_two_slot_claims_have_one_winner() throws Exception {
		LocalDate date = LocalDate.of(2026, 9, 8);
		LocalTime start = LocalTime.of(10, 0);
		LocalTime end = LocalTime.of(10, 30);

		List<Optional<Appointment>> results = race(
				() -> this.appointmentService.tryBook(1, 1, date, start, end, "race proof", "staff"),
				() -> this.appointmentService.tryBook(2, 1, date, start, end, "race proof", "staff"));

		assertThat(results).filteredOn(Optional::isPresent).hasSize(1);
		assertThat(results).filteredOn(Optional::isEmpty).hasSize(1);
		assertThat(this.appointments.findAll()).singleElement().satisfies(appointment -> {
			assertThat(appointment.getVet().getId()).isEqualTo(1);
			assertThat(appointment.getDate()).isEqualTo(date);
			assertThat(appointment.getStartTime()).isEqualTo(start);
			assertThat(appointment.getEndTime()).isEqualTo(end);
			assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
		});
		assertThat(this.appointments.countByStatusIn(List.of(AppointmentStatus.HELD, AppointmentStatus.CONFIRMED)))
			.isEqualTo(1);
	}

	@Test
	void uc5G7ConcurrentReschedulesYieldOneWinnerAndPreserveTheLosingAppointment() throws Exception {
		LocalDate date = LocalDate.of(2026, 9, 8);
		Appointment first = this.staffCalendarService.book(1, 1, date, LocalTime.of(9, 0), 30, "First booking",
				"staff");
		Appointment second = this.staffCalendarService.book(2, 2, date, LocalTime.of(9, 0), 30, "Second booking",
				"staff");

		List<String> results = race(() -> rescheduleOutcome(first.getId(), "First moved"),
				() -> rescheduleOutcome(second.getId(), "Second moved"));

		assertThat(results).containsExactlyInAnyOrder("MOVED", "UNAVAILABLE");
		assertThat(this.appointments.findAll()).hasSize(2);
		assertThat(this.appointments.findAll())
			.filteredOn(appointment -> appointment.getVet().getId() == 3
					&& appointment.getStartTime().equals(LocalTime.of(10, 0)))
			.singleElement()
			.satisfies(
					appointment -> assertThat(appointment.getLastChangeReason()).isIn("First moved", "Second moved"));
		assertThat(this.appointments.findAll())
			.filteredOn(appointment -> appointment.getStartTime().equals(LocalTime.of(9, 0)))
			.singleElement()
			.satisfies(appointment -> assertThat(appointment.getLastChangeReason()).isIn("First booking",
					"Second booking"));
	}

	@Test
	void uc3G7_concurrentOwnerConfirmationsYieldOneDentistryHold() throws Exception {
		int first = interpretedDentistryRequest(1);
		int second = interpretedDentistryRequest(2);

		List<SchedulingRequest> results = race(() -> this.requestService.confirmInterpretation(first),
				() -> this.requestService.confirmInterpretation(second));

		assertThat(this.jdbcTemplate.queryForList(
				"select state from scheduling_requests where id in (?, ?) order by state", String.class, first, second))
			.containsExactly("SUGGESTION_OFFERED", "WITH_STAFF");
		assertThat(results).extracting(SchedulingRequest::getState)
			.containsExactlyInAnyOrder(RequestState.SUGGESTION_OFFERED, RequestState.WITH_STAFF);
		assertThat(this.appointments.findAll()).singleElement().satisfies(appointment -> {
			assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.HELD);
			assertThat(appointment.getVet().getId()).isEqualTo(3);
			assertThat(appointment.getDate()).isEqualTo(LocalDate.of(2026, 9, 8));
			assertThat(appointment.getStartTime()).isEqualTo(LocalTime.of(9, 0));
		});
	}

	private int interpretedDentistryRequest(int petId) {
		int requestId = this.requestService.startForOwner(petId, "Dentistry for pet " + petId).request().getId();
		this.jdbcTemplate.update("update scheduling_requests set state = 'INTERPRETING' where id = ?", requestId);
		Interpretation interpretation = new Interpretation(true, CareType.SPECIALTY, "dentistry", null, 30, null,
				InterpretationOrigin.AI, "{}", "ministral-3:14b", "v1", LocalDate.of(2026, 9, 7), LocalTime.of(9, 0));
		interpretation.addWindow(
				InterpretationWindow.preferred(LocalDate.of(2026, 9, 8), LocalTime.of(9, 0), LocalTime.of(9, 30)));
		this.requestService.interpretationSucceeded(requestId, interpretation);
		return requestId;
	}

	private String staffSuggestionOutcome(SchedulingRequest request,
			SlotSuggestionPort.StaffSuggestionCommand command) {
		try {
			RequestService.ActionResult result = this.requestService.placeStaffSuggestion(request.getId(),
					request.getVersion(), command, "Concurrent staff selection", "staff");
			return result.messageKey() == null ? "COMPLETED" : result.messageKey();
		}
		catch (IllegalStateException ex) {
			return "UNAVAILABLE";
		}
	}

	private String rescheduleOutcome(int appointmentId, String reason) {
		try {
			this.staffCalendarService.reschedule(appointmentId, 3, LocalDate.of(2026, 9, 8), LocalTime.of(10, 0),
					reason, "staff");
			return "MOVED";
		}
		catch (StaffSlotUnavailableException ex) {
			return "UNAVAILABLE";
		}
	}

	private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<T> firstResult = executor.submit(awaitStart(first, ready, start));
			Future<T> secondResult = executor.submit(awaitStart(second, ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private <T> Callable<T> awaitStart(Callable<T> action, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
			return action.call();
		};
	}

}
