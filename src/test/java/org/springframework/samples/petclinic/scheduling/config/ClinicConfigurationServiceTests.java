package org.springframework.samples.petclinic.scheduling.config;

import java.sql.Date;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.appointment.StaffCalendarQueryService;
import org.springframework.samples.petclinic.scheduling.interpretation.PromptBuilder;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailability;
import org.springframework.samples.petclinic.scheduling.matching.StaffSlotValidator;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.support.TestClockConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClinicConfigurationServiceTests {

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

	private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 10);

	@Autowired
	private ClinicConfigurationService configurations;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PromptBuilder prompts;

	@Autowired
	private AvailabilityService availability;

	@Autowired
	private StaffSlotValidator staffSlots;

	@Autowired
	private StaffCalendarQueryService calendar;

	@Autowired
	private RequestService requestService;

	@Test
	void uc7MainAndG3EveryScheduleTypeListsAllConfirmedConflictsAndPreservesHolds() {
		int firstConfirmed = insertAppointment(null, 1, 4, THURSDAY, LocalTime.of(9, 0), "CONFIRMED");
		int secondConfirmed = insertAppointment(null, 2, 4, THURSDAY, LocalTime.of(10, 0), "CONFIRMED");
		int heldRequest = insertRequest(3, "SUGGESTION_OFFERED");
		int held = insertAppointment(heldRequest, 3, 4, THURSDAY, LocalTime.of(11, 0), "HELD");
		DatabaseSnapshot before = snapshot();
		ClinicConfiguration current = this.configurations.current();

		List<Function<ClinicConfiguration, ClinicConfiguration>> scheduleChanges = List.of(
				configuration -> withSchedule(configuration,
						configuration.openingHours()
							.stream()
							.map(hours -> hours.weekday() == DayOfWeek.THURSDAY
									? new ClinicConfiguration.OpeningPeriod(DayOfWeek.THURSDAY, null, null) : hours)
							.toList(),
						configuration.workingPeriods(), configuration.exceptions(), configuration.leavePeriods(),
						configuration.closures()),
				configuration -> withSchedule(configuration, configuration.openingHours(),
						configuration.workingPeriods()
							.stream()
							.filter(period -> period.veterinarianId() != 4 || period.weekday() != DayOfWeek.THURSDAY)
							.toList(),
						configuration.exceptions(), configuration.leavePeriods(), configuration.closures()),
				configuration -> withSchedule(configuration, configuration.openingHours(),
						configuration.workingPeriods(),
						append(configuration.exceptions(), new ClinicConfiguration.VetExceptionDate(4, THURSDAY)),
						configuration.leavePeriods(), configuration.closures()),
				configuration -> withSchedule(configuration, configuration.openingHours(),
						configuration.workingPeriods(), configuration.exceptions(),
						append(configuration.leavePeriods(),
								new ClinicConfiguration.LeavePeriod(4, THURSDAY, THURSDAY)),
						configuration.closures()),
				configuration -> withSchedule(configuration, configuration.openingHours(),
						configuration.workingPeriods(), configuration.exceptions(), configuration.leavePeriods(),
						append(configuration.closures(), THURSDAY)));

		for (Function<ClinicConfiguration, ClinicConfiguration> change : scheduleChanges) {
			ClinicConfigurationService.ChangeResult result = this.configurations.change(change.apply(current));
			assertThat(result.saved()).isFalse();
			assertThat(result.confirmedConflicts()).extracting(ClinicConfigurationService.Conflict::appointmentId)
				.containsExactly(firstConfirmed, secondConfirmed);
			assertThat(snapshot()).isEqualTo(before);
			assertThat(this.jdbc.queryForObject("select count(*) from appointments where id = ?", Integer.class, held))
				.isOne();
			assertThat(requestRow(heldRequest)).containsEntry("STATE", "SUGGESTION_OFFERED")
				.containsEntry("WITH_STAFF_REASON", null);
		}
	}

	@Test
	void uc7MainAndG4HoldOnlyConflictSavesAtomicallyAndRoutesEveryRequest() {
		int firstRequest = insertRequest(1, "SUGGESTION_OFFERED");
		int firstHeld = insertAppointment(firstRequest, 1, 4, THURSDAY, LocalTime.of(9, 0), "HELD");
		int secondRequest = insertRequest(2, "SUGGESTION_OFFERED");
		int secondHeld = insertAppointment(secondRequest, 2, 6, THURSDAY, LocalTime.of(10, 0), "HELD");
		ClinicConfiguration current = this.configurations.current();
		ClinicConfiguration proposed = withSchedule(current, current.openingHours(), current.workingPeriods(),
				current.exceptions(), current.leavePeriods(), append(current.closures(), THURSDAY));

		ClinicConfigurationService.ChangeResult result = this.configurations.change(proposed);

		assertThat(result.saved()).isTrue();
		assertThat(result.confirmedConflicts()).isEmpty();
		assertThat(result.affectedRequests()).extracting(ClinicConfigurationService.AffectedRequest::requestId)
			.containsExactly(firstRequest, secondRequest);
		assertThat(this.jdbc.queryForList("select id from appointments where id in (?, ?)", firstHeld, secondHeld))
			.isEmpty();
		assertThat(requestRow(firstRequest)).containsEntry("STATE", "WITH_STAFF")
			.containsEntry("WITH_STAFF_REASON", "SCHEDULE_CHANGED");
		assertThat(requestRow(secondRequest)).containsEntry("STATE", "WITH_STAFF")
			.containsEntry("WITH_STAFF_REASON", "SCHEDULE_CHANGED");
		assertThat(this.jdbc.queryForObject("select count(*) from clinic_closures where closure_date = ?",
				Integer.class, Date.valueOf(THURSDAY)))
			.isOne();
	}

	@Test
	void uc7MainExtension5aAndG1G2G5SavedValuesDriveEveryConsumer() {
		ClinicConfiguration current = this.configurations.current();
		List<ClinicConfiguration.OpeningPeriod> hours = current.openingHours()
			.stream()
			.map(value -> value.weekday() == DayOfWeek.THURSDAY
					? new ClinicConfiguration.OpeningPeriod(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(18, 0))
					: value)
			.toList();
		List<ClinicConfiguration.WorkingPeriod> periods = current.workingPeriods()
			.stream()
			.filter(value -> value.veterinarianId() != 4 || value.weekday() != DayOfWeek.THURSDAY)
			.collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
		periods
			.add(new ClinicConfiguration.WorkingPeriod(4, DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));
		periods.add(
				new ClinicConfiguration.WorkingPeriod(4, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(18, 0)));
		ClinicConfiguration proposed = new ClinicConfiguration(45, 2, 20, 45, 75, current.gridMinutes(),
				LocalTime.of(8, 0), LocalTime.NOON, LocalTime.NOON, LocalTime.of(17, 0), LocalTime.of(17, 0),
				LocalTime.of(19, 0), "Europe/Paris", hours, periods, current.exceptions(),
				append(current.leavePeriods(),
						new ClinicConfiguration.LeavePeriod(2, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29))),
				append(current.closures(), LocalDate.of(2026, 12, 25)));

		ClinicConfigurationService.ChangeResult result = this.configurations.change(proposed);

		assertThat(result.saved()).isTrue();
		assertThat(result.affectedRequests()).isEmpty();
		ClinicConfiguration saved = this.configurations.current();
		assertThat(saved.bookingHorizonDays()).isEqualTo(45);
		assertThat(saved.minimumLeadDays()).isEqualTo(2);
		assertThat(saved.minimumDurationMinutes()).isEqualTo(20);
		assertThat(saved.defaultDurationMinutes()).isEqualTo(45);
		assertThat(saved.maximumDurationMinutes()).isEqualTo(75);
		assertThat(saved.timeZone()).isEqualTo("Europe/Paris");
		assertThat(saved.workingPeriods()).containsAll(periods);
		assertThat(saved.leavePeriods())
			.contains(new ClinicConfiguration.LeavePeriod(2, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)));
		assertThat(saved.closures()).contains(LocalDate.of(2026, 12, 25));

		assertThat(this.prompts.build("visit", TODAY)).contains("THURSDAY:08:00-18:00",
				"morning:08:00-12:00,afternoon:12:00-17:00,evening:17:00-19:00", "zone=Europe/Paris",
				"durationMinutes=min:20,default:45,max:75");
		assertThat(this.availability.getEffectiveAvailability(4, THURSDAY))
			.extracting(EffectiveAvailability::startTime, EffectiveAvailability::endTime)
			.containsExactly(org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0), LocalTime.NOON),
					org.assertj.core.groups.Tuple.tuple(LocalTime.of(13, 0), LocalTime.of(18, 0)));
		assertThat(this.staffSlots.refusalFor(4, THURSDAY, LocalTime.of(13, 0), 45)).isNull();
		assertThat(this.calendar.calendar(THURSDAY).clinicClosed()).isFalse();

		int requestId = insertInterpretedRequest();
		this.requestService.confirmInterpretation(requestId);
		assertThat(this.jdbc.queryForMap("select * from appointments where request_id = ?", requestId))
			.containsEntry("VET_ID", 4)
			.containsEntry("APPOINTMENT_DATE", Date.valueOf(THURSDAY))
			.containsEntry("START_TIME", Time.valueOf(LocalTime.of(13, 0)))
			.containsEntry("END_TIME", Time.valueOf(LocalTime.of(13, 45)));
	}

	@Test
	void uc7Extension3aInvalidRangesAndOverlapsRollBackTheWholeDatabase() {
		ClinicConfiguration current = this.configurations.current();
		DatabaseSnapshot before = snapshot();
		ClinicConfiguration badDuration = new ClinicConfiguration(current.bookingHorizonDays(),
				current.minimumLeadDays(), 60, 30, 45, current.gridMinutes(), current.morningStart(),
				current.morningEnd(), current.afternoonStart(), current.afternoonEnd(), current.eveningStart(),
				current.eveningEnd(), current.timeZone(), current.openingHours(), current.workingPeriods(),
				current.exceptions(), current.leavePeriods(), current.closures());
		assertRejectedWithoutMutation(badDuration, "scheduling.settings.validation.duration", before);

		List<ClinicConfiguration.OpeningPeriod> reversed = current.openingHours()
			.stream()
			.map(value -> value.weekday() == DayOfWeek.MONDAY
					? new ClinicConfiguration.OpeningPeriod(DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(9, 0))
					: value)
			.toList();
		assertRejectedWithoutMutation(withSchedule(current, reversed, current.workingPeriods(), current.exceptions(),
				current.leavePeriods(), current.closures()), "scheduling.settings.validation.openingHours", before);

		List<ClinicConfiguration.WorkingPeriod> overlap = append(current.workingPeriods(),
				new ClinicConfiguration.WorkingPeriod(1, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)));
		assertRejectedWithoutMutation(withSchedule(current, current.openingHours(), overlap, current.exceptions(),
				current.leavePeriods(), current.closures()), "scheduling.settings.validation.overlap", before);
	}

	private void assertRejectedWithoutMutation(ClinicConfiguration configuration, String key, DatabaseSnapshot before) {
		assertThatThrownBy(() -> this.configurations.change(configuration))
			.isInstanceOf(ConfigurationValidationException.class)
			.hasMessage(key);
		assertThat(snapshot()).isEqualTo(before);
	}

	private int insertInterpretedRequest() {
		int requestId = nextId("scheduling_requests");
		this.jdbc.update("""
				insert into scheduling_requests
				(id, pet_id, active_pet_id, request_text, state, created_date, created_time, failure_count, version)
				values (?, 1, 1, 'Thursday afternoon visit', 'INTERPRETED', ?, ?, 0, 0)
				""", requestId, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 0)));
		int interpretationId = nextId("interpretations");
		this.jdbc.update("""
				insert into interpretations
				(id, request_id, understood, care_type, specialty, duration_minutes, preferred_vet_id, origin,
				 created_date, created_time)
				values (?, ?, true, 'SPECIALTY', 'surgery', null, 4, 'STAFF', ?, ?)
				""", interpretationId, requestId, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 0)));
		this.jdbc.update("""
				insert into interpretation_windows
				(interpretation_id, window_kind, weekday, start_time, end_time)
				values (?, 'ALLOWED', 'THURSDAY', '13:00:00', '17:00:00')
				""", interpretationId);
		this.jdbc.update("update scheduling_requests set current_interpretation_id = ? where id = ?", interpretationId,
				requestId);
		return requestId;
	}

	private int insertRequest(int petId, String state) {
		int id = nextId("scheduling_requests");
		this.jdbc.update("""
				insert into scheduling_requests
				(id, pet_id, active_pet_id, request_text, state, created_date, created_time, failure_count, version)
				values (?, ?, ?, 'Configuration conflict fixture', ?, ?, ?, 0, 0)
				""", id, petId, petId, state, Date.valueOf(TODAY), Time.valueOf(LocalTime.of(9, 0)));
		return id;
	}

	private int insertAppointment(Integer requestId, int petId, int veterinarianId, LocalDate date, LocalTime startTime,
			String status) {
		int id = nextId("appointments");
		LocalTime endTime = startTime.plusMinutes(30);
		this.jdbc.update("""
				insert into appointments
				(id, request_id, pet_id, vet_id, appointment_date, start_time, end_time, status,
				 held_date, held_time, created_date, created_time)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", id, requestId, petId, veterinarianId, Date.valueOf(date), Time.valueOf(startTime),
				Time.valueOf(endTime), status, "HELD".equals(status) ? Date.valueOf(TODAY) : null,
				"HELD".equals(status) ? Time.valueOf(LocalTime.of(9, 0)) : null, Date.valueOf(TODAY),
				Time.valueOf(LocalTime.of(9, 0)));
		return id;
	}

	private Map<String, Object> requestRow(int id) {
		return this.jdbc.queryForMap("select * from scheduling_requests where id = ?", id);
	}

	private int nextId(String table) {
		return this.jdbc.queryForObject("select coalesce(max(id), 0) + 1 from " + table, Integer.class);
	}

	private DatabaseSnapshot snapshot() {
		return new DatabaseSnapshot(rows("clinic_settings"), rows("clinic_opening_hours"), rows("vet_working_blocks"),
				rows("vet_exceptions"), rows("vet_leave"), rows("clinic_closures"), rows("scheduling_requests"),
				rows("appointments"));
	}

	private List<Map<String, Object>> rows(String table) {
		return this.jdbc.queryForList("select * from " + table + " order by id");
	}

	private static <T> List<T> append(List<T> values, T value) {
		java.util.ArrayList<T> result = new java.util.ArrayList<>(values);
		result.add(value);
		return result;
	}

	private static ClinicConfiguration withSchedule(ClinicConfiguration source,
			List<ClinicConfiguration.OpeningPeriod> openingHours,
			List<ClinicConfiguration.WorkingPeriod> workingPeriods,
			List<ClinicConfiguration.VetExceptionDate> exceptions, List<ClinicConfiguration.LeavePeriod> leavePeriods,
			List<LocalDate> closures) {
		return new ClinicConfiguration(source.bookingHorizonDays(), source.minimumLeadDays(),
				source.minimumDurationMinutes(), source.defaultDurationMinutes(), source.maximumDurationMinutes(),
				source.gridMinutes(), source.morningStart(), source.morningEnd(), source.afternoonStart(),
				source.afternoonEnd(), source.eveningStart(), source.eveningEnd(), source.timeZone(), openingHours,
				workingPeriods, exceptions, leavePeriods, closures);
	}

	private record DatabaseSnapshot(List<Map<String, Object>> settings, List<Map<String, Object>> openingHours,
			List<Map<String, Object>> workingBlocks, List<Map<String, Object>> exceptions,
			List<Map<String, Object>> leave, List<Map<String, Object>> closures, List<Map<String, Object>> requests,
			List<Map<String, Object>> appointments) {
	}

}
