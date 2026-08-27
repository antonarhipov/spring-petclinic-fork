package org.springframework.samples.petclinic.scheduling.dto;

public record AvailabilityIntervalViewDto(String kind, String startLocal, String endLocal) {
}
