package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public record ClinicConfiguration(int bookingHorizonDays, int minimumLeadDays, int minimumDurationMinutes,
		int defaultDurationMinutes, int maximumDurationMinutes, int gridMinutes, LocalTime morningStart,
		LocalTime morningEnd, LocalTime afternoonStart, LocalTime afternoonEnd, LocalTime eveningStart,
		LocalTime eveningEnd, String timeZone, List<OpeningPeriod> openingHours, List<WorkingPeriod> workingPeriods,
		List<VetExceptionDate> exceptions, List<LeavePeriod> leavePeriods, List<LocalDate> closures) {

	public ClinicConfiguration {
		openingHours = List.copyOf(openingHours);
		workingPeriods = List.copyOf(workingPeriods);
		exceptions = List.copyOf(exceptions);
		leavePeriods = List.copyOf(leavePeriods);
		closures = List.copyOf(closures);
	}

	public Optional<OpeningPeriod> openingHoursFor(DayOfWeek weekday) {
		return this.openingHours.stream().filter(hours -> hours.weekday() == weekday).findFirst();
	}

	public record OpeningPeriod(DayOfWeek weekday, LocalTime openTime, LocalTime closeTime) {

		public boolean isOpen() {
			return this.openTime != null && this.closeTime != null;
		}

	}

	public record WorkingPeriod(int veterinarianId, DayOfWeek weekday, LocalTime startTime, LocalTime endTime) {
	}

	public record VetExceptionDate(int veterinarianId, LocalDate date) {
	}

	public record LeavePeriod(int veterinarianId, LocalDate startDate, LocalDate endDate) {

		public boolean covers(LocalDate date) {
			return !date.isBefore(this.startDate) && !date.isAfter(this.endDate);
		}

	}

}
