package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityQueryService {

	private final ClinicSchedulingSettingsRepository settings;

	private final ClinicClosureRepository closures;

	private final VetLeaveRepository leaves;

	private final VetAvailabilityExceptionRepository exceptions;

	private final RecurringVetShiftRepository shifts;

	public AvailabilityQueryService(ClinicSchedulingSettingsRepository settings, ClinicClosureRepository closures,
			VetLeaveRepository leaves, VetAvailabilityExceptionRepository exceptions,
			RecurringVetShiftRepository shifts) {
		this.settings = settings;
		this.closures = closures;
		this.leaves = leaves;
		this.exceptions = exceptions;
		this.shifts = shifts;
	}

	@Transactional(readOnly = true)
	public boolean isAvailable(Integer vetId, Instant start, int durationMinutes) {
		ClinicSchedulingSettings clinic = this.settings.findById(1).orElseThrow();
		ZoneId zone = ZoneId.of(clinic.getClinicZone());
		LocalDateTime localStart = start.atZone(zone).toLocalDateTime();
		LocalDateTime localEnd = localStart.plusMinutes(durationMinutes);
		if (!localStart.toLocalDate().equals(localEnd.toLocalDate()) || localStart.getMinute() % 15 != 0) {
			return false;
		}
		LocalDate date = localStart.toLocalDate();
		if (this.closures.findAll().stream().anyMatch(closure -> closure.applies(date))
				|| this.leaves.findByVetId(vetId).stream().anyMatch(leave -> leave.applies(date))) {
			return false;
		}
		return this.exceptions.findByVetId(vetId)
			.stream()
			.filter(exception -> exception.applies(date))
			.findFirst()
			.map(exception -> exception.isAvailable()
					&& fits(exception.getStartTime(), exception.getEndTime(), localStart, localEnd))
			.orElseGet(() -> this.shifts.findByVetIdAndDayOfWeek(vetId, date.getDayOfWeek().getValue())
				.stream()
				.anyMatch(shift -> fits(shift.getStartTime(), shift.getEndTime(), localStart, localEnd)));
	}

	private boolean fits(java.time.LocalTime availableStart, java.time.LocalTime availableEnd, LocalDateTime start,
			LocalDateTime end) {
		return availableStart != null && availableEnd != null && !start.toLocalTime().isBefore(availableStart)
				&& !end.toLocalTime().isAfter(availableEnd);
	}

}
