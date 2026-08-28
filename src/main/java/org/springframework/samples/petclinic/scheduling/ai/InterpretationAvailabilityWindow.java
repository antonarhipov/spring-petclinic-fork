package org.springframework.samples.petclinic.scheduling.ai;

import org.jspecify.annotations.Nullable;

public record InterpretationAvailabilityWindow(@Nullable String applicableDate, @Nullable Integer dayOfWeek,
		String startTime, String endTime) {

}
