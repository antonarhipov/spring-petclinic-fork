package org.springframework.samples.petclinic.scheduling.matching;

import java.util.Set;

public record VetFact(int veterinarianId, Set<Integer> specialtyIds) {
}
