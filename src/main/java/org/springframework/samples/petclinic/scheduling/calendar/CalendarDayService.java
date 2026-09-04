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

package org.springframework.samples.petclinic.scheduling.calendar;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.model.NamedEntity;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigService;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetException;
import org.springframework.samples.petclinic.scheduling.clinic.VetExceptionRepository;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlock;
import org.springframework.samples.petclinic.scheduling.clinic.VetWeeklyBlockRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service calculating the five calendar capacity layers, slot grid, and day navigation
 * (AC-96, AC-98, RULE-33).
 */
@Service
public class CalendarDayService {

	private static final LocalTime GRID_START = LocalTime.of(8, 0);

	private static final LocalTime GRID_END = LocalTime.of(19, 0);

	private static final int GRID_STEP_MINUTES = 15;

	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

	private final Clock clock;

	private final ClinicConfigService configService;

	private final ClinicOpeningHourRepository openingHourRepository;

	private final VetRepository vetRepository;

	private final VetWeeklyBlockRepository weeklyBlockRepository;

	private final VetExceptionRepository exceptionRepository;

	private final AppointmentRepository appointmentRepository;

	private final SchedulingRequestRepository requestRepository;

	private final JdbcTemplate jdbcTemplate;

	public CalendarDayService(Clock clock, ClinicConfigService configService,
			ClinicOpeningHourRepository openingHourRepository, VetRepository vetRepository,
			VetWeeklyBlockRepository weeklyBlockRepository, VetExceptionRepository exceptionRepository,
			AppointmentRepository appointmentRepository, SchedulingRequestRepository requestRepository,
			JdbcTemplate jdbcTemplate) {
		this.clock = clock;
		this.configService = configService;
		this.openingHourRepository = openingHourRepository;
		this.vetRepository = vetRepository;
		this.weeklyBlockRepository = weeklyBlockRepository;
		this.exceptionRepository = exceptionRepository;
		this.appointmentRepository = appointmentRepository;
		this.requestRepository = requestRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional(readOnly = true)
	public CalendarDayView getDayView(LocalDate date) {
		ZoneId zoneId = this.clock.getZone();
		LocalDate today = LocalDate.now(this.clock);
		LocalDate targetDate = (date != null) ? date : today;
		LocalDate prevDate = targetDate.minusDays(1);
		LocalDate nextDate = targetDate.plusDays(1);

		ClinicConfig config = this.configService.current();
		int horizonDays = config.getBookingHorizonDays();
		LocalDate minDate = today;
		LocalDate maxDate = today.plusDays(horizonDays);

		// Clinic opening hours & closure
		boolean isClosure = isClinicClosedByClosure(targetDate);
		ClinicOpeningHour openingHour = findOpeningHour(targetDate);
		boolean clinicClosed = isClosure || openingHour == null || openingHour.isClosed();

		String clinicHoursSummary;
		if (clinicClosed) {
			clinicHoursSummary = "Closed";
		}
		else {
			clinicHoursSummary = openingHour.getOpenTime().format(TIME_FORMATTER) + " - "
					+ openingHour.getCloseTime().format(TIME_FORMATTER);
		}

		List<Vet> vets = this.vetRepository.findAll().stream().sorted(Comparator.comparing(Vet::getId)).toList();

		ZonedDateTime startOfDay = targetDate.atStartOfDay(zoneId);
		ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(zoneId);

		List<Appointment> dayAppointments = this.appointmentRepository.findConfirmedInDateRange(startOfDay, endOfDay);
		List<SchedulingRequest> activeHolds = this.requestRepository.findAllActiveHolds()
			.stream()
			.filter(r -> r.getHeldStart() != null
					&& r.getHeldStart().withZoneSameInstant(zoneId).toLocalDate().equals(targetDate))
			.toList();

		List<CalendarDayView.VetColumn> vetColumns = new ArrayList<>();
		List<VetWorkingInfo> vetInfos = new ArrayList<>();

		for (Vet vet : vets) {
			String specialties = vet.getSpecialties()
				.stream()
				.map(Specialty::getName)
				.collect(Collectors.joining(", "));
			if (specialties.isBlank()) {
				specialties = "general";
			}

			List<TimeBlock> effectiveBlocks = computeEffectiveBlocks(vet.getId(), targetDate, openingHour,
					clinicClosed);
			String effectiveBlocksSummary = effectiveBlocks.isEmpty() ? "None"
					: effectiveBlocks.stream()
						.map(b -> b.start().format(TIME_FORMATTER) + " - " + b.end().format(TIME_FORMATTER))
						.collect(Collectors.joining(", "));

			List<Appointment> vetAppointments = dayAppointments.stream()
				.filter(a -> Objects.equals(a.getVet().getId(), vet.getId()))
				.toList();

			List<SchedulingRequest> vetHolds = activeHolds.stream()
				.filter(r -> r.getHeldVet() != null && Objects.equals(r.getHeldVet().getId(), vet.getId()))
				.toList();

			int bookedCount = vetAppointments.size();
			int bookedMinutes = vetAppointments.stream().mapToInt(Appointment::getDuration).sum();

			int heldCount = vetHolds.size();
			int heldMinutes = vetHolds.stream().mapToInt(SchedulingRequest::getHeldDuration).sum();

			int totalWorkingMinutes = effectiveBlocks.stream()
				.mapToInt(b -> (int) java.time.Duration.between(b.start(), b.end()).toMinutes())
				.sum();

			int remainingCapacityMinutes = Math.max(0, totalWorkingMinutes - bookedMinutes - heldMinutes);

			vetColumns.add(new CalendarDayView.VetColumn(vet.getId(), vet.getFirstName() + " " + vet.getLastName(),
					specialties, clinicHoursSummary, effectiveBlocksSummary, bookedCount, bookedMinutes, heldCount,
					heldMinutes, remainingCapacityMinutes));

			vetInfos.add(new VetWorkingInfo(vet, effectiveBlocks, vetAppointments, vetHolds));
		}

		// Grid rows
		List<CalendarDayView.GridRow> gridRows = new ArrayList<>();
		LocalTime currentSlot = GRID_START;

		while (currentSlot.isBefore(GRID_END)) {
			LocalTime nextSlot = currentSlot.plusMinutes(GRID_STEP_MINUTES);
			String timeLabel = currentSlot.format(TIME_FORMATTER);

			List<CalendarDayView.Cell> cells = new ArrayList<>();

			for (VetWorkingInfo info : vetInfos) {
				Vet vet = info.vet();
				CalendarDayView.Cell cell = computeCell(vet, currentSlot, nextSlot, openingHour, clinicClosed, info);
				cells.add(cell);
			}

			gridRows.add(new CalendarDayView.GridRow(currentSlot, timeLabel, cells));
			currentSlot = nextSlot;
		}

		return new CalendarDayView(targetDate, prevDate, nextDate, today, minDate, maxDate, clinicHoursSummary,
				clinicClosed, vetColumns, gridRows);
	}

	private CalendarDayView.Cell computeCell(Vet vet, LocalTime slotStart, LocalTime slotEnd,
			ClinicOpeningHour openingHour, boolean clinicClosed, VetWorkingInfo info) {
		String timeLabel = slotStart.format(TIME_FORMATTER);

		if (clinicClosed || openingHour == null || slotStart.isBefore(openingHour.getOpenTime())
				|| !slotEnd.isBefore(openingHour.getCloseTime().plusSeconds(1))) {
			return new CalendarDayView.Cell(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), slotStart,
					timeLabel, "closed", null, null, "Closed");
		}

		boolean isWorking = info.effectiveBlocks()
			.stream()
			.anyMatch(b -> !slotStart.isBefore(b.start()) && !slotEnd.isAfter(b.end()));

		if (!isWorking) {
			return new CalendarDayView.Cell(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), slotStart,
					timeLabel, "off-shift", null, null, "Off-shift");
		}

		ZoneId zoneId = this.clock.getZone();

		// Check booked appointment
		for (Appointment app : info.appointments()) {
			LocalTime appStart = app.getStartTime().withZoneSameInstant(zoneId).toLocalTime();
			LocalTime appEnd = appStart.plusMinutes(app.getDuration());
			if (!slotStart.isBefore(appStart) && slotStart.isBefore(appEnd)) {
				String label = (app.getPet() != null ? app.getPet().getName() : "Pet") + " ("
						+ (app.getReason() != null ? app.getReason() : "Appointment") + ")";
				return new CalendarDayView.Cell(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), slotStart,
						timeLabel, "booked", app.getId(), null, label);
			}
		}

		// Check held request
		for (SchedulingRequest req : info.holds()) {
			LocalTime holdStart = req.getHeldStart().withZoneSameInstant(zoneId).toLocalTime();
			LocalTime holdEnd = holdStart.plusMinutes(req.getHeldDuration());
			if (!slotStart.isBefore(holdStart) && slotStart.isBefore(holdEnd)) {
				return new CalendarDayView.Cell(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), slotStart,
						timeLabel, "held", null, req.getId(), "Held");
			}
		}

		return new CalendarDayView.Cell(vet.getId(), vet.getFirstName() + " " + vet.getLastName(), slotStart, timeLabel,
				"free", null, null, "Free");
	}

	private List<TimeBlock> computeEffectiveBlocks(Integer vetId, LocalDate date, ClinicOpeningHour openingHour,
			boolean clinicClosed) {
		if (clinicClosed || openingHour == null || openingHour.isClosed()) {
			return List.of();
		}

		if (isVetOnLeave(vetId, date)) {
			return List.of();
		}

		List<VetException> exceptions = this.exceptionRepository.findByVetId(vetId)
			.stream()
			.filter(e -> e.getExceptionDate().equals(date))
			.toList();

		if (!exceptions.isEmpty() && exceptions.stream().anyMatch(VetException::isUnavailable)) {
			return List.of();
		}

		DayOfWeek dayOfWeek = date.getDayOfWeek();
		List<VetWeeklyBlock> weeklyBlocks = this.weeklyBlockRepository.findByVetId(vetId)
			.stream()
			.filter(b -> b.getDayOfWeek() == dayOfWeek)
			.toList();

		List<TimeBlock> result = new ArrayList<>();
		LocalTime clinicOpen = openingHour.getOpenTime();
		LocalTime clinicClose = openingHour.getCloseTime();

		for (VetWeeklyBlock block : weeklyBlocks) {
			LocalTime start = block.getStartTime().isBefore(clinicOpen) ? clinicOpen : block.getStartTime();
			LocalTime end = block.getEndTime().isAfter(clinicClose) ? clinicClose : block.getEndTime();
			if (start.isBefore(end)) {
				result.add(new TimeBlock(start, end));
			}
		}

		result.sort(Comparator.comparing(TimeBlock::start));
		return result;
	}

	private boolean isClinicClosedByClosure(LocalDate date) {
		try {
			Integer count = this.jdbcTemplate
				.queryForObject("SELECT count(*) FROM clinic_closure WHERE closure_date = ?", Integer.class, date);
			return count != null && count > 0;
		}
		catch (Exception ex) {
			return false;
		}
	}

	private boolean isVetOnLeave(Integer vetId, LocalDate date) {
		try {
			Integer count = this.jdbcTemplate.queryForObject(
					"SELECT count(*) FROM vet_leave WHERE vet_id = ? AND start_date <= ? AND end_date >= ?",
					Integer.class, vetId, date, date);
			return count != null && count > 0;
		}
		catch (Exception ex) {
			return false;
		}
	}

	private ClinicOpeningHour findOpeningHour(LocalDate date) {
		DayOfWeek dayOfWeek = date.getDayOfWeek();
		return this.openingHourRepository.findAll()
			.stream()
			.filter(h -> h.getDayOfWeek() == dayOfWeek)
			.findFirst()
			.orElse(null);
	}

	private record TimeBlock(LocalTime start, LocalTime end) {
	}

	private record VetWorkingInfo(Vet vet, List<TimeBlock> effectiveBlocks, List<Appointment> appointments,
			List<SchedulingRequest> holds) {
	}

}
