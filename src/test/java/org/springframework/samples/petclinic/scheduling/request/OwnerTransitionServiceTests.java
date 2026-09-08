package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
@ActiveProfiles("test")
class OwnerTransitionServiceTests {

	private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

	private static final LocalTime TIME = LocalTime.of(10, 15, 30);

	@Autowired
	private RequestService requestService;

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

	@MockitoBean
	private InterpretationLauncher interpretationLauncher;

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
	@Tag("AC-34")
	void ac34_decline_routes_to_staff() {
		int requestId = this.requestService.createForOwner(1, "Cat checkup").getId();

		SchedulingRequest declined = this.requestService.decline(requestId);
		assertThat(declined.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(declined.getWithStaffReason()).isEqualTo(WithStaffReason.DECLINED_CONSENT);
		verifyNoInteractions(this.interpretationLauncher);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.WITH_STAFF);
			assertThat(reloaded.getWithStaffReason()).isEqualTo(WithStaffReason.DECLINED_CONSENT);
			assertThat(reloaded.getInterpretations()).isEmpty();

			Integer appointmentCount = this.jdbc
				.queryForObject("select count(*) from appointments where request_id = ?", Integer.class, requestId);
			assertThat(appointmentCount).isZero();
			return null;
		});
	}

	@Test
	@Tag("AC-35")
	void ac35_edits_from_three_non_hold_states_return_to_consent() {
		// State 1: AWAITING_CONSENT
		int id1 = this.requestService.createForOwner(1, "Initial text 1").getId();
		addRejectionToRequest(id1);

		this.requestService.editText(id1, "Edited text 1");
		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(id1).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
			assertThat(reloaded.getRequestText()).isEqualTo("Edited text 1");
			assertThat(reloaded.getRejections()).hasSize(1);
			return null;
		});

		clearDatabase();

		// State 2: INTERPRETATION_FAILED
		int id2 = this.requestService.createForOwner(1, "Initial text 2").getId();
		addRejectionToRequest(id2);
		this.requestService.consent(id2);
		this.requestService.interpretationFailed(id2,
				new InterpretationFailure(InterpretationFailureKind.UNPARSEABLE, "bad", DATE, TIME));

		this.requestService.editText(id2, "Edited text 2");
		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(id2).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
			assertThat(reloaded.getRequestText()).isEqualTo("Edited text 2");
			assertThat(reloaded.getRejections()).hasSize(1);
			return null;
		});

		clearDatabase();

		// State 3: INTERPRETED
		int id3 = this.requestService.createForOwner(1, "Initial text 3").getId();
		addRejectionToRequest(id3);
		this.requestService.consent(id3);
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(id3, interpretation);

		this.requestService.editText(id3, "Edited text 3");
		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(id3).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
			assertThat(reloaded.getRequestText()).isEqualTo("Edited text 3");
			assertThat(reloaded.getRejections()).hasSize(1);
			assertThat(reloaded.getCurrentInterpretation()).isNull();
			assertThat(reloaded.getInterpretations()).hasSize(1);
			return null;
		});
	}

	@Test
	@Tag("AC-36")
	void ac36_suggestion_edit_deletes_hold_and_keeps_rejections() {
		int requestId = this.requestService.createForOwner(1, "Surgery needed").getId();
		addRejectionToRequest(requestId);
		this.requestService.consent(requestId);
		Interpretation interpretation = new Interpretation(true, CareType.GENERAL, null, null, 30, null,
				InterpretationOrigin.AI, "{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(requestId, interpretation);

		// Place hold and transition to SUGGESTION_OFFERED
		this.transactionTemplate.execute(status -> {
			SchedulingRequest req = this.requests.findById(requestId).orElseThrow();
			req.transitionTo(RequestState.SUGGESTION_OFFERED, DATE, TIME);
			this.requests.save(req);

			Vet vet = this.entityManager.find(Vet.class, 1);
			Appointment held = Appointment.held(req, vet, DATE, TIME, TIME.plusMinutes(30), "rank", DATE, TIME);
			this.appointments.save(held);
			return null;
		});

		Integer heldBefore = this.jdbc.queryForObject(
				"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class, requestId);
		assertThat(heldBefore).isEqualTo(1);

		this.requestService.editText(requestId, "New text after seeing suggestion");

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			assertThat(reloaded.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
			assertThat(reloaded.getRequestText()).isEqualTo("New text after seeing suggestion");
			assertThat(reloaded.getRejections()).hasSize(1);

			Integer heldAfter = this.jdbc.queryForObject(
					"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
					requestId);
			assertThat(heldAfter).isZero();
			return null;
		});
	}

	@Test
	@Tag("AC-37")
	void ac37_abandon_every_non_terminal_state_closes_cleanly() {
		List<RequestState> nonTerminalStates = List.of(RequestState.AWAITING_CONSENT, RequestState.INTERPRETING,
				RequestState.INTERPRETATION_FAILED, RequestState.INTERPRETED, RequestState.SUGGESTION_OFFERED,
				RequestState.WITH_STAFF);

		for (RequestState state : nonTerminalStates) {
			clearDatabase();
			int requestId = this.requestService.createForOwner(1, "Abandon test for " + state).getId();

			this.transactionTemplate.execute(status -> {
				SchedulingRequest req = this.requests.findById(requestId).orElseThrow();
				if (state == RequestState.SUGGESTION_OFFERED) {
					Vet vet = this.entityManager.find(Vet.class, 1);
					Appointment held = Appointment.held(req, vet, DATE, TIME, TIME.plusMinutes(30), "rank", DATE, TIME);
					this.appointments.save(held);
				}
				req.transitionTo(state, DATE, TIME);
				this.requests.save(req);
				return null;
			});

			this.requestService.abandon(requestId);

			this.transactionTemplate.execute(status -> {
				SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
				assertThat(reloaded.getState()).isEqualTo(RequestState.ABANDONED);
				assertThat(reloaded.getActivePetId()).isNull();

				Integer heldCount = this.jdbc.queryForObject(
						"select count(*) from appointments where request_id = ? and status = 'HELD'", Integer.class,
						requestId);
				assertThat(heldCount).isZero();
				return null;
			});
		}

		// Terminal states cannot be abandoned
		int terminalId = this.requestService.createForOwner(1, "Terminal test").getId();
		this.requestService.abandon(terminalId);
		assertThatThrownBy(() -> this.requestService.abandon(terminalId))
			.isInstanceOf(IllegalRequestTransitionException.class);
	}

	@Test
	@Tag("AC-44")
	void ac44_absent_values_default_only_at_match() {
		int requestId = this.requestService.createForOwner(1, "Needs general check").getId();
		this.requestService.consent(requestId);

		Interpretation interpretation = new Interpretation(true, null, null, null, null, null, InterpretationOrigin.AI,
				"{\"understood\":true}", "ministral-3:14b", "v1", DATE, TIME);
		this.requestService.interpretationSucceeded(requestId, interpretation);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			Interpretation loaded = reloaded.getCurrentInterpretation();
			assertThat(loaded).isNotNull();
			assertThat(loaded.getCareType()).isNull();
			assertThat(loaded.getDurationMinutes()).isNull();

			Map<String, Object> row = this.jdbc
				.queryForMap("select care_type, duration_minutes from interpretations where id = ?", loaded.getId());
			assertThat(row.get("care_type")).isNull();
			assertThat(row.get("duration_minutes")).isNull();

			assertThat(loaded.getEffectiveCareType()).isEqualTo(CareType.GENERAL);
			assertThat(loaded.getEffectiveDurationMinutes(30)).isEqualTo(30);
			return null;
		});

		this.requestService.confirmInterpretation(requestId);

		this.transactionTemplate.execute(status -> {
			SchedulingRequest reloaded = this.requests.findById(requestId).orElseThrow();
			Interpretation loaded = reloaded.getCurrentInterpretation();
			assertThat(loaded.getCareType()).isNull();
			assertThat(loaded.getDurationMinutes()).isNull();

			Map<String, Object> row = this.jdbc
				.queryForMap("select care_type, duration_minutes from interpretations where id = ?", loaded.getId());
			assertThat(row.get("care_type")).isNull();
			assertThat(row.get("duration_minutes")).isNull();
			return null;
		});
	}

	private void addRejectionToRequest(int requestId) {
		this.transactionTemplate.execute(status -> {
			SchedulingRequest req = this.requests.findById(requestId).orElseThrow();
			Vet vet = this.entityManager.find(Vet.class, 1);
			req.addRejection(vet, DATE, TIME, DATE, TIME);
			this.requests.save(req);
			return null;
		});
	}

}
