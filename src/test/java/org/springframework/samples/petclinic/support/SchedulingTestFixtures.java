package org.springframework.samples.petclinic.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationCandidate;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowCandidate;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;

public final class SchedulingTestFixtures {

	private SchedulingTestFixtures() {
	}

	public static Clock fixedClock(Instant instant, ZoneId zoneId) {
		return Clock.fixed(instant, zoneId);
	}

	public static InterpretationCandidate routineCandidate(LocalDate date, LocalTime start, LocalTime end,
			Integer durationMinutes) {
		WindowCandidate window = new WindowCandidate(WindowClassification.PREFERRED, WindowShape.ONE_OFF, date, null,
				null, null, start, end, "test window", null);
		return new InterpretationCandidate("1", "Routine annual wellness exam", Urgency.ROUTINE, List.of(),
				durationMinutes != null ? durationMinutes : 30, null, null, List.of(window), List.of(), List.of());
	}

	public static InterpretationCandidate priorityCandidate(LocalDate date, LocalTime start, LocalTime end) {
		WindowCandidate window = new WindowCandidate(WindowClassification.PREFERRED, WindowShape.ONE_OFF, date, null,
				null, null, start, end, "test window", null);
		return new InterpretationCandidate("1", "Ear infection worsening quickly", Urgency.PRIORITY, List.of(), 30,
				null, null, List.of(window), List.of(), List.of());
	}

	public static InterpretationCandidate emergencyCandidate() {
		return new InterpretationCandidate("1", "Severe bleeding from paw and difficulty breathing",
				Urgency.EMERGENCY_SUSPECTED, List.of("bleeding"), 30, null, null, List.of(), List.of(), List.of());
	}

}
