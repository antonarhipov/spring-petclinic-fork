package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.appointment.ReservationResourceType;

public record OccupancyFact(ReservationResourceType resourceType, int resourceId, Instant blockStart) {
}
