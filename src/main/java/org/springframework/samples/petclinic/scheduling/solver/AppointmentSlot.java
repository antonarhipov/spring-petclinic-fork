/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.ZonedDateTime;

import org.springframework.samples.petclinic.vet.Vet;

/** One already-enumerated candidate; it is the model's only planning variable. */
public record AppointmentSlot(Vet vet, ZonedDateTime startTime) {
}
