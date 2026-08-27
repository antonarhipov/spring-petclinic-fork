package org.springframework.samples.petclinic.scheduling.dto;

public record AvailabilityWindowViewDto(int order, String kind, String dateExpression, String calendarParameters,
		String timeParameters) {
}
