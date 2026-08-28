package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Instant;

import org.springframework.samples.petclinic.vet.Vet;

public record CandidateSlot(Vet veterinarian, Instant startAt) {
}
