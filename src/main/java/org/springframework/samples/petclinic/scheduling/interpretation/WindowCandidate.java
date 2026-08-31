package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WindowCandidate(WindowClassification classification, WindowShape shape,
		@JsonFormat(pattern = "yyyy-MM-dd") LocalDate date, @JsonFormat(pattern = "yyyy-MM-dd") LocalDate rangeStart,
		@JsonFormat(pattern = "yyyy-MM-dd") LocalDate rangeEnd, List<DayOfWeek> weekdays,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime, @JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String sourceText, String resolutionNote) {
}
