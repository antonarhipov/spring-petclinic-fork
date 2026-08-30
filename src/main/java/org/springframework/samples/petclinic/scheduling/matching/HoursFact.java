package org.springframework.samples.petclinic.scheduling.matching;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record HoursFact(Integer veterinarianId, DayOfWeek dayOfWeek, LocalTime startLocal, LocalTime endLocal) {
}
