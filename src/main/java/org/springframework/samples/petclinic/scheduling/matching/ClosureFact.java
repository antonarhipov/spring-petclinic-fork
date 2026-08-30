package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;

public record ClosureFact(LocalDate startDate, LocalDate endDate) {
}
