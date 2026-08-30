package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;

public record VetLeaveFact(Integer veterinarianId, LocalDate startDate, LocalDate endDate) {
}
