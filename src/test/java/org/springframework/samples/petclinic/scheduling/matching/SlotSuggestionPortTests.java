package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
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
import org.springframework.samples.petclinic.scheduling.request.WithStaffReason;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SlotSuggestionPortTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

	private static final LocalTime TIME = LocalTime.of(10, 15, 30);

	@Autowired
	private RequestService requestService;

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

	@Autowired
	private SchedulingRequestRepository requests;

	@Autowired
	private AppointmentRepository appointments;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private Clock clock;

	private TransactionTemplate transactionTemplate;

	@BeforeEach
	void setUp() {
		this.transactionTemplate = new TransactionTemplate(this.transactionManager);
		clearDatabase();
	}

	private void clearDatabase() {
		this.transactionTemplate.execute(status -> {
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
	@Tag("AC-74")
	void ac74_top_candidate_held_and_suggestion_offered() {
		int requestId = startRequestWithWindows();

		SchedulingRequest confirmed = this.requestService.confirmInterpretation(requestId);
		assertThat(confirmed.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);

			List<Appointment> heldList = this.appointments.findAll()
				.stream()
				.filter(a -> a.getRequest() != null && a.getRequest().getId() == requestId)
				.toList();
			assertThat(heldList).hasSize(1);
			Appointment held = heldList.get(0);
			assertThat(held.getStatus()).isEqualTo(AppointmentStatus.HELD);
			assertThat(held.getRankReason()).isNotNull().startsWith("scheduling.slot.");

			Integer countInDb = this.jdbc.queryForObject(
					"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
					requestId);
			assertThat(countInDb).isEqualTo(1);
			return null;
		});
	}

	@Test
	void uc3G5_onlyConfirmedAppointmentsAffectTheWorkloadTieBreak() {
		SchedulingRequest staffRequest = this.requestService.createForStaff(2, "Hold later time for workload test")
			.request();
		this.requestService.placeStaffSuggestion(staffRequest.getId(), staffRequest.getVersion(),
				new SlotSuggestionPort.StaffSuggestionCommand(1, DATE.plusDays(1), LocalTime.of(11, 0), 30));

		int ownerRequestId = startRequestWithWindows();
		this.requestService.confirmInterpretation(ownerRequestId);

		Map<String, Object> held = this.jdbc.queryForMap(
				"select vet_id, appointment_date, start_time from appointments where request_id = ? and status = 'HELD'",
				ownerRequestId);
		assertThat(held).containsEntry("VET_ID", 1);
		assertThat(held.get("APPOINTMENT_DATE").toString()).isEqualTo(DATE.plusDays(1).toString());
		assertThat(held.get("START_TIME").toString()).startsWith("09:00");
	}

	@Test
	void uc3G12_seededDateExceptionAffectsMatchingAtThePinnedClinicClock() {
		int requestId = this.requestService.createForOwner(1, "September 15 morning").getId();
		this.requestService.consent(requestId);
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation.addWindow(
				InterpretationWindow.preferred(LocalDate.of(2026, 9, 15), LocalTime.of(9, 0), LocalTime.of(12, 0)));
		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.requestService.confirmInterpretation(requestId);

		Map<String, Object> held = this.jdbc.queryForMap(
				"select vet_id, appointment_date, start_time from appointments where request_id = ? and status = 'HELD'",
				requestId);
		assertThat(held).containsEntry("VET_ID", 2);
		assertThat(held.get("APPOINTMENT_DATE").toString()).isEqualTo("2026-09-15");
		assertThat(held.get("START_TIME").toString()).startsWith("09:00");
	}

	@Test
	void uc3Rule20_staffContinuationUsesTheSameEffectiveAvailability() {
		SchedulingRequest staffRequest = this.requestService.createForStaff(2, "Invalid Saturday suggestion").request();

		assertThatThrownBy(() -> this.requestService.placeStaffSuggestion(staffRequest.getId(),
				staffRequest.getVersion(),
				new SlotSuggestionPort.StaffSuggestionCommand(1, LocalDate.of(2026, 9, 12), LocalTime.of(10, 0), 30)))
			.isInstanceOf(IllegalStateException.class);

		SchedulingRequest reloaded = this.requests.findById(staffRequest.getId()).orElseThrow();
		assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where request_id = ?", Integer.class,
				staffRequest.getId()))
			.isZero();
	}

	@Test
	@Tag("AC-76")
	void ac76_replacement_releases_old_hold_and_creates_new() {
		int requestId = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId);

		Appointment oldHeld = this.transactionTemplate.execute(status -> {
			return this.appointments.findAll()
				.stream()
				.filter(a -> a.getRequest() != null && a.getRequest().getId() == requestId)
				.findFirst()
				.orElseThrow();
		});

		int oldHeldId = oldHeld.getId();
		int oldVetId = oldHeld.getVet().getId();
		LocalDate oldDate = oldHeld.getDate();
		LocalTime oldStartTime = oldHeld.getStartTime();

		SchedulingRequest replaced = this.requestService.anotherSuggestion(requestId);
		assertThat(replaced.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);

		this.transactionTemplate.execute(status -> {
			List<Appointment> heldList = this.appointments.findAll()
				.stream()
				.filter(a -> a.getRequest() != null && a.getRequest().getId() == requestId)
				.toList();
			assertThat(heldList).hasSize(1);
			Appointment newHeld = heldList.get(0);
			assertThat(newHeld.getId()).isNotEqualTo(oldHeldId);

			boolean sameSlot = newHeld.getVet().getId() == oldVetId && newHeld.getDate().equals(oldDate)
					&& newHeld.getStartTime().equals(oldStartTime);
			assertThat(sameSlot).isFalse();

			Integer rejectionCount = this.jdbc.queryForObject(
					"select count(*) from request_rejections where request_id = ? and vet_id = ? and appointment_date = ? and start_time = ?",
					Integer.class, requestId, oldVetId, oldDate, oldStartTime);
			assertThat(rejectionCount).isEqualTo(1);
			return null;
		});
	}

	@Test
	@Tag("AC-79")
	void ac79_decline_and_abandon_release_hold() {
		// Case A: abandon releases hold
		int requestId1 = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId1);

		Integer heldBeforeAbandon = this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
				requestId1);
		assertThat(heldBeforeAbandon).isEqualTo(1);

		this.requestService.abandon(requestId1);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId1).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.ABANDONED);

			Integer heldAfter = this.jdbc.queryForObject("select count(*) from appointments where request_id = ?",
					Integer.class, requestId1);
			assertThat(heldAfter).isZero();
			return null;
		});

		clearDatabase();

		// Case B: route to staff releases hold
		int requestId2 = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId2);

		Integer heldBeforeRoute = this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
				requestId2);
		assertThat(heldBeforeRoute).isEqualTo(1);

		this.requestService.routeToStaff(requestId2);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId2).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);

			Integer heldAfter = this.jdbc.queryForObject("select count(*) from appointments where request_id = ?",
					Integer.class, requestId2);
			assertThat(heldAfter).isZero();
			return null;
		});
	}

	@Test
	void uc3Extension8e_scheduleInvalidationDeletesTheHoldAndRoutesToStaff() {
		int requestId = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId);
		int heldId = this.jdbc.queryForObject("select id from appointments where request_id = ? and status = 'HELD'",
				Integer.class, requestId);

		SchedulingRequest changed = this.requestService.scheduleChanged(requestId);

		assertThat(changed.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(changed.getWithStaffReason()).isEqualTo(WithStaffReason.SCHEDULE_CHANGED);
		assertThat(this.appointments.findById(heldId)).isEmpty();
		assertThat(this.jdbc.queryForObject("select count(*) from appointments where request_id = ?", Integer.class,
				requestId))
			.isZero();
	}

	@Test
	void uc3Extension8d_staffReleaseRequiresAReasonBeforeDeletingTheHold() {
		int requestId = startRequestWithWindows();
		SchedulingRequest offered = this.requestService.confirmInterpretation(requestId);
		int heldId = this.jdbc.queryForObject("select id from appointments where request_id = ? and status = 'HELD'",
				Integer.class, requestId);

		RequestService.ActionResult refused = this.requestService.releaseHold(requestId, offered.getVersion(), " ");

		assertThat(refused.messageKey()).isEqualTo("scheduling.action.requiredReason");
		assertThat(this.requests.findById(requestId).orElseThrow().getState())
			.isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(this.appointments.findById(heldId)).isPresent();
	}

	@Test
	void uc3Rule22_rejectionSurvivesEditReinterpretationAndRematch() {
		int requestId = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId);
		Appointment first = this.appointments.findAll().get(0);
		int firstVetId = first.getVet().getId();
		LocalDate firstDate = first.getDate();
		LocalTime firstStart = first.getStartTime();

		this.requestService.anotherSuggestion(requestId);
		int replacementId = this.jdbc.queryForObject(
				"select id from appointments where request_id = ? and status = 'HELD'", Integer.class, requestId);
		this.requestService.editText(requestId, "Rephrased request with the same availability");
		assertThat(this.appointments.findById(replacementId)).isEmpty();

		this.requestService.consent(requestId);
		addInterpretation(requestId);
		this.requestService.confirmInterpretation(requestId);

		Map<String, Object> rematched = this.jdbc.queryForMap(
				"select vet_id, appointment_date, start_time from appointments where request_id = ? and status = 'HELD'",
				requestId);
		boolean sameRejectedSlot = ((Number) rematched.get("VET_ID")).intValue() == firstVetId
				&& rematched.get("APPOINTMENT_DATE").toString().equals(firstDate.toString())
				&& LocalTime.parse(rematched.get("START_TIME").toString()).equals(firstStart);
		assertThat(sameRejectedSlot).isFalse();
		assertThat(this.jdbc.queryForObject(
				"select count(*) from request_rejections where request_id = ? and vet_id = ? and appointment_date = ? and start_time = ?",
				Integer.class, requestId, firstVetId, firstDate, firstStart))
			.isOne();
		assertThat(this.jdbc.queryForObject("select count(*) from interpretations where request_id = ?", Integer.class,
				requestId))
			.isEqualTo(2);
	}

	@Test
	void uc3Extension9a_unavailableHeldSlotIsReplacedWithoutAnErrorPageState() {
		int requestId = startRequestWithWindows();
		this.requestService.confirmInterpretation(requestId);
		Appointment original = this.appointments.findAll().get(0);
		this.jdbc.update(
				"insert into appointments(request_id, pet_id, vet_id, appointment_date, start_time, end_time, status, created_date, created_time) values (null, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?)",
				2, original.getVet().getId(), original.getDate(), original.getStartTime(), original.getEndTime(), DATE,
				TIME);

		SchedulingRequest result = this.requestService.acceptSuggestion(requestId);

		assertThat(result.getState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(this.appointments.findById(original.getId())).isEmpty();
		assertThat(this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class, requestId))
			.isOne();
		assertThat(this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and vet_id = ? and appointment_date = ? and start_time = ?",
				Integer.class, requestId, original.getVet().getId(), original.getDate(), original.getStartTime()))
			.isZero();
	}

	private int startRequestWithWindows() {
		int requestId = this.requestService.createForOwner(1, "Cat checkup appointment").getId();
		this.requestService.consent(requestId);
		addInterpretation(requestId);
		return requestId;
	}

	private void addInterpretation(int requestId) {
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		interpretation
			.addWindow(InterpretationWindow.preferred(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));
		interpretation
			.addWindow(InterpretationWindow.allowed(DayOfWeek.WEDNESDAY, LocalTime.of(13, 0), LocalTime.of(17, 0)));

		this.requestService.interpretationSucceeded(requestId, interpretation);
	}

}
