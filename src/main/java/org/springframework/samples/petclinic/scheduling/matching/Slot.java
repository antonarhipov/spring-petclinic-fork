package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

public record Slot(int vetId, LocalDate date, LocalTime startTime, LocalTime endTime, WindowType windowType) {

	public Slot(int vetId, LocalDate date, LocalTime startTime, LocalTime endTime) {
		this(vetId, date, startTime, endTime, WindowType.ALLOWED);
	}

	public int durationMinutes() {
		return (int) Duration.between(this.startTime, this.endTime).toMinutes();
	}

	public Slot withWindowType(WindowType type) {
		return new Slot(this.vetId, this.date, this.startTime, this.endTime, type);
	}

}
