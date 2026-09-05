/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.clinic.AvailabilityConflictService;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicClosureRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDay;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.clinic.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetAvailabilityService;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetLeaveRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEventRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.ClinicSettingsForm;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AvailabilityConflictTests {

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	@Autowired
	private Clock clock;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private SchedulingRequestEventRepository eventRepository;

	@Autowired
	private OwnerRepository ownerRepository;

	@Autowired
	private VetRepository vetRepository;

	@Autowired
	private VetExceptionRepository exceptionRepository;

	@Autowired
	private VetLeaveRepository leaveRepository;

	@Autowired
	private ClinicClosureRepository closureRepository;

	@Autowired
	private ClinicSettingsService settingsService;

	@Autowired
	private EffectiveAvailabilityService availabilityService;

	@Test
	@Tag("AC-102")
	void eachEditTypeWithConfirmedConflictRefusesAndListsAll_AC102() {
		List<EditCase> cases = List.of(new EditCase(EditKind.OPENING_HOURS, LocalDate.of(2026, 9, 15)),
				new EditCase(EditKind.EXCEPTION, LocalDate.of(2026, 9, 16)),
				new EditCase(EditKind.LEAVE, LocalDate.of(2026, 9, 17)),
				new EditCase(EditKind.CLOSURE, LocalDate.of(2026, 9, 18)));

		for (EditCase editCase : cases) {
			Appointment first = saveAppointment(1, editCase.date(), LocalTime.of(10, 0));
			Appointment second = saveAppointment(editCase.kind() == EditKind.CLOSURE ? 2 : 1, editCase.date(),
					LocalTime.of(11, 0));
			saveAppointment(editCase.kind() == EditKind.OPENING_HOURS ? 1 : 2,
					editCase.kind() == EditKind.CLOSURE ? editCase.date().plusDays(1) : editCase.date(),
					LocalTime.of(9, 0));
			this.entityManager.flush();
			Map<String, List<String>> before = databaseSnapshot();

			EditOutcome outcome = applyEdit(editCase);

			assertThat(outcome.applied()).as(editCase.kind().name()).isFalse();
			assertThat(outcome.conflictIds()).as("complete conflict list for " + editCase.kind())
				.containsExactly(first.getId(), second.getId());
			assertThat(outcome.invalidatedHoldIds()).isEmpty();
			this.entityManager.flush();
			assertThat(databaseSnapshot()).as("whole database snapshot for " + editCase.kind()).isEqualTo(before);
		}
	}

	@Test
	@Tag("AC-103")
	void holdOnlyConflictInvalidatesHoldsAndApplies_AC103() {
		List<EditCase> cases = List.of(new EditCase(EditKind.OPENING_HOURS, LocalDate.of(2026, 9, 22)),
				new EditCase(EditKind.EXCEPTION, LocalDate.of(2026, 9, 23)),
				new EditCase(EditKind.LEAVE, LocalDate.of(2026, 9, 24)),
				new EditCase(EditKind.CLOSURE, LocalDate.of(2026, 9, 25)));

		for (EditCase editCase : cases) {
			SchedulingRequest first = saveHold(1, editCase.date(), LocalTime.of(10, 0));
			SchedulingRequest second = saveHold(editCase.kind() == EditKind.CLOSURE ? 2 : 1, editCase.date(),
					LocalTime.of(11, 0));
			SchedulingRequest unaffected = saveHold(editCase.kind() == EditKind.OPENING_HOURS ? 1 : 2,
					editCase.kind() == EditKind.CLOSURE ? editCase.date().plusDays(1) : editCase.date(),
					LocalTime.of(9, 0));
			this.entityManager.flush();
			HoldSnapshot unaffectedBefore = holdSnapshot(unaffected);
			long unaffectedEventsBefore = this.eventRepository.findByRequestIdOrderByTimestampAsc(unaffected.getId())
				.size();

			EditOutcome outcome = applyEdit(editCase);

			assertThat(outcome.applied()).as(editCase.kind().name()).isTrue();
			assertThat(outcome.conflictIds()).isEmpty();
			assertThat(outcome.invalidatedHoldIds()).containsExactly(first.getId(), second.getId());
			this.entityManager.flush();
			this.entityManager.clear();
			assertInvalidated(first.getId());
			assertInvalidated(second.getId());
			SchedulingRequest unaffectedAfter = this.requestRepository.findById(unaffected.getId()).orElseThrow();
			assertThat(holdSnapshot(unaffectedAfter)).as("unaffected hold for " + editCase.kind())
				.isEqualTo(unaffectedBefore);
			assertThat(this.eventRepository.findByRequestIdOrderByTimestampAsc(unaffected.getId()))
				.hasSize((int) unaffectedEventsBefore);
			assertEditPersisted(editCase);
		}
	}

	private EditOutcome applyEdit(EditCase editCase) {
		return switch (editCase.kind()) {
			case OPENING_HOURS -> {
				ClinicSettingsForm form = currentSettingsForm();
				String day = editCase.date().getDayOfWeek().name();
				form.getOpeningHours().put(day + "_closed", "false");
				form.getOpeningHours().put(day + "_open", "09:00");
				form.getOpeningHours().put(day + "_close", "10:00");
				AvailabilityConflictService.EditResult<Void> result = this.settingsService.updateSettings(form,
						"staff");
				yield outcome(result);
			}
			case EXCEPTION -> {
				List<VetAvailabilityService.VetExceptionDto> exceptions = new ArrayList<>(
						this.availabilityService.getAvailabilityData(1)
							.exceptions()
							.stream()
							.map(exception -> new VetAvailabilityService.VetExceptionDto(exception.getExceptionDate(),
									exception.isUnavailable(), exception.getStartTime(), exception.getEndTime()))
							.toList());
				exceptions.add(new VetAvailabilityService.VetExceptionDto(editCase.date(), true, null, null));
				yield outcome(this.availabilityService.saveSchedule(1, null, exceptions, null, null, "staff"));
			}
			case LEAVE -> {
				List<VetAvailabilityService.VetLeaveDto> leaves = new ArrayList<>(
						this.availabilityService.getAvailabilityData(1)
							.leaves()
							.stream()
							.map(leave -> new VetAvailabilityService.VetLeaveDto(leave.getStartDate(),
									leave.getEndDate(), leave.getReason()))
							.toList());
				leaves.add(new VetAvailabilityService.VetLeaveDto(editCase.date(), editCase.date(), "training"));
				yield outcome(this.availabilityService.saveSchedule(1, null, null, leaves, null, "staff"));
			}
			case CLOSURE -> {
				List<VetAvailabilityService.ClinicClosureDto> closures = new ArrayList<>(
						this.availabilityService.getAvailabilityData(1)
							.closures()
							.stream()
							.map(closure -> new VetAvailabilityService.ClinicClosureDto(closure.getClosureDate(),
									closure.getReason()))
							.toList());
				closures.add(new VetAvailabilityService.ClinicClosureDto(editCase.date(), "maintenance"));
				yield outcome(this.availabilityService.saveSchedule(1, null, null, null, closures, "staff"));
			}
		};
	}

	private EditOutcome outcome(AvailabilityConflictService.EditResult<Void> result) {
		return new EditOutcome(result.applied(), result.conflicts().stream().map(conflict -> conflict.id()).toList(),
				result.invalidatedHoldIds());
	}

	private EditOutcome outcome(EffectiveAvailabilityService.AvailabilityUpdateResult result) {
		return new EditOutcome(result.success(), result.conflicts().stream().map(conflict -> conflict.id()).toList(),
				result.invalidatedHoldIds());
	}

	private Appointment saveAppointment(Integer vetId, LocalDate date, LocalTime time) {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().getFirst();
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		Appointment appointment = new Appointment();
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartTime(date.atTime(time).atZone(this.clock.getZone()));
		appointment.setDuration(30);
		appointment.setStatus(AppointmentStatus.CONFIRMED);
		appointment.setReason("conflict fixture");
		return this.appointmentRepository.save(appointment);
	}

	private SchedulingRequest saveHold(Integer vetId, LocalDate date, LocalTime time) {
		Owner owner = this.ownerRepository.findById(1).orElseThrow();
		Pet pet = owner.getPets().getFirst();
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		ZonedDateTime created = ZonedDateTime.now(this.clock).minusMinutes(5);
		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setState(RequestState.SUGGESTION_OFFERED);
		request.setReasonText("hold fixture");
		request.setAvailabilityText("fixture availability");
		request.setActivePetId(null);
		request.setCreatedAt(created);
		request.setUpdatedAt(created);
		request.setHold(vet, date.atTime(time).atZone(this.clock.getZone()), 30);
		return this.requestRepository.save(request);
	}

	private void assertInvalidated(Integer requestId) {
		SchedulingRequest request = this.requestRepository.findById(requestId).orElseThrow();
		assertThat(request.getState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(request.getHeldVet()).isNull();
		assertThat(request.getHeldStart()).isNull();
		assertThat(request.getHeldDuration()).isNull();
		List<SchedulingRequestEvent> events = this.eventRepository.findByRequestIdOrderByTimestampAsc(requestId);
		assertThat(events).hasSize(1);
		SchedulingRequestEvent event = events.getFirst();
		assertThat(event.getFromState()).isEqualTo(RequestState.SUGGESTION_OFFERED);
		assertThat(event.getToState()).isEqualTo(RequestState.WITH_STAFF);
		assertThat(event.getActor()).isEqualTo("staff");
		assertThat(event.getAction()).isEqualTo("HOLD_INVALIDATED");
		assertThat(event.getReason()).isEqualTo("availability edit");
		assertThat(event.getPayload()).isNull();
		assertThat(event.getTimestamp()).isEqualTo(request.getUpdatedAt());
	}

	private HoldSnapshot holdSnapshot(SchedulingRequest request) {
		return new HoldSnapshot(request.getState(), request.getHeldVet().getId(), request.getHeldStart().toInstant(),
				request.getHeldDuration(), request.getUpdatedAt().toInstant());
	}

	private void assertEditPersisted(EditCase editCase) {
		switch (editCase.kind()) {
			case OPENING_HOURS -> {
				ClinicOpeningHour row = this.settingsService.allOpeningHours()
					.stream()
					.filter(hour -> hour.getDayOfWeek() == editCase.date().getDayOfWeek())
					.findFirst()
					.orElseThrow();
				assertThat(row.isClosed()).isFalse();
				assertThat(row.getOpenTime()).isEqualTo(LocalTime.of(9, 0));
				assertThat(row.getCloseTime()).isEqualTo(LocalTime.of(10, 0));
			}
			case EXCEPTION -> assertThat(this.exceptionRepository.findByVetId(1)).anySatisfy(exception -> {
				assertThat(exception.getExceptionDate()).isEqualTo(editCase.date());
				assertThat(exception.isUnavailable()).isTrue();
				assertThat(exception.getStartTime()).isNull();
				assertThat(exception.getEndTime()).isNull();
			});
			case LEAVE -> assertThat(this.leaveRepository.findByVetId(1)).anySatisfy(leave -> {
				assertThat(leave.getStartDate()).isEqualTo(editCase.date());
				assertThat(leave.getEndDate()).isEqualTo(editCase.date());
				assertThat(leave.getReason()).isEqualTo("training");
			});
			case CLOSURE -> assertThat(this.closureRepository.findAll()).anySatisfy(closure -> {
				assertThat(closure.getClosureDate()).isEqualTo(editCase.date());
				assertThat(closure.getReason()).isEqualTo("maintenance");
			});
		}
	}

	private ClinicSettingsForm currentSettingsForm() {
		ClinicConfig config = this.settingsService.current();
		ClinicSettingsForm form = new ClinicSettingsForm();
		form.setHorizonDays(config.getBookingHorizonDays());
		form.setMinDurationMinutes(config.getMinDurationMinutes());
		form.setMaxDurationMinutes(config.getMaxDurationMinutes());
		form.setDefaultDurationMinutes(config.getDefaultDurationMinutes());
		form.setEmergencyPhone(config.getEmergencyPhone());
		form.setTimeZone(config.getTimeZone());
		Map<String, String> hours = new LinkedHashMap<>();
		for (ClinicOpeningHour opening : this.settingsService.allOpeningHours()) {
			String day = opening.getDayOfWeek().name();
			hours.put(day + "_closed", String.valueOf(opening.isClosed()));
			hours.put(day + "_open",
					opening.getOpenTime() == null ? "09:00" : opening.getOpenTime().format(TIME_FORMATTER));
			hours.put(day + "_close",
					opening.getCloseTime() == null ? "17:00" : opening.getCloseTime().format(TIME_FORMATTER));
		}
		form.setOpeningHours(hours);
		for (ClinicPartOfDay part : this.settingsService.allPartsOfDay()) {
			if ("morning".equalsIgnoreCase(part.getName())) {
				form.setMorningStart(part.getStartTime().format(TIME_FORMATTER));
				form.setMorningEnd(part.getEndTime().format(TIME_FORMATTER));
			}
			else if ("afternoon".equalsIgnoreCase(part.getName())) {
				form.setAfternoonStart(part.getStartTime().format(TIME_FORMATTER));
				form.setAfternoonEnd(part.getEndTime().format(TIME_FORMATTER));
			}
			else if ("evening".equalsIgnoreCase(part.getName())) {
				form.setEveningStart(part.getStartTime().format(TIME_FORMATTER));
				form.setEveningEnd(part.getEndTime().format(TIME_FORMATTER));
			}
		}
		return form;
	}

	private Map<String, List<String>> databaseSnapshot() {
		List<String> tables = this.jdbcTemplate.queryForList(
				"SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE' AND UPPER(TABLE_NAME) <> 'FLYWAY_SCHEMA_HISTORY' ORDER BY TABLE_NAME",
				String.class);
		Map<String, List<String>> snapshot = new LinkedHashMap<>();
		for (String table : tables) {
			List<String> rows = this.jdbcTemplate.queryForList("SELECT * FROM " + table)
				.stream()
				.map(row -> row.toString())
				.sorted()
				.toList();
			snapshot.put(table, rows);
		}
		return snapshot;
	}

	private enum EditKind {

		OPENING_HOURS, EXCEPTION, LEAVE, CLOSURE

	}

	private record EditCase(EditKind kind, LocalDate date) {
	}

	private record EditOutcome(boolean applied, List<Integer> conflictIds, List<Integer> invalidatedHoldIds) {
	}

	private record HoldSnapshot(RequestState state, Integer vetId, java.time.Instant start, Integer duration,
			java.time.Instant updatedAt) {
	}

}
