package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;

public record CalendarItemDto(Integer id, String itemCategory, String status, String vetName, String ownerName,
		String petName, Instant startTime, Instant endTime, String details) {
}
