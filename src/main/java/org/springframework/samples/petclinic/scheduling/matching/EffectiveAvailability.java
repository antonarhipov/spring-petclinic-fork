package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.time.LocalTime;

public record EffectiveAvailability(int vetId, LocalDate date, LocalTime startTime, LocalTime endTime) {

	public boolean contains(LocalTime start, LocalTime end) {
		return !start.isBefore(this.startTime) && !end.isAfter(this.endTime);
	}

	public boolean contains(LocalDate targetDate, LocalTime start, LocalTime end) {
		return this.date.equals(targetDate) && contains(start, end);
	}

}
