package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;
import java.util.List;

public record SuggestionDto(Integer reservationId, Integer vetId, String vetName, List<String> specialties,
		Instant startTime, Instant endTime, Integer durationMinutes, Instant expiresAt, Long requestVersion) {
}
