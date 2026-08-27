package org.springframework.samples.petclinic.scheduling.dto;

import java.util.List;

public record AIInterpretationResponseDto(String summary, String careType, String specialtyName,
		String preferredVetName, Integer durationMinutes, boolean urgent, List<TimeWindowDto> windows,
		List<String> issues) {
	public record TimeWindowDto(String kind, String dateExpression, String explicitDate, String dayOfWeek,
			Integer weekOffset, Integer monthOffset, String month, String ordinalWeek, String startTime, String endTime,
			String namedPeriod) {
	}
}
