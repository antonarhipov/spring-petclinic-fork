package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.availability.AvailabilityPrecedence.Interval;

public record VetDateExceptionFact(Integer veterinarianId, LocalDate date, List<Interval> intervals) {
}
