package org.springframework.samples.petclinic.scheduling.ai;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class InterpretationValidator {

	private static final Set<Integer> DURATIONS = Set.of(15, 30, 45, 60);

	public void validate(InterpretationResponse response) {
		if (response == null || blank(response.visitReason()) || blank(response.careType())
				|| response.durationMinutes() == null) {
			throw new IllegalArgumentException("Interpretation is missing required scheduling fields");
		}
		if (!DURATIONS.contains(response.durationMinutes())) {
			throw new IllegalArgumentException("Interpretation duration is not configured");
		}
		if ("UNCERTAIN".equalsIgnoreCase(response.urgency()) || "UNCERTAIN".equalsIgnoreCase(response.careType())) {
			throw new IllegalArgumentException("Safety-critical interpretation uncertainty requires staff review"
					+ " (careType=" + response.careType() + ", urgency=" + response.urgency() + ")");
		}
		if (response.preferredWindows().size() > 10) {
			throw new IllegalArgumentException("Interpretation contains too many preferred windows");
		}
		response.preferredWindows().forEach(this::validateWindow);
	}

	private void validateWindow(InterpretationAvailabilityWindow window) {
		if (window == null || (window.applicableDate() == null) == (window.dayOfWeek() == null)) {
			throw new IllegalArgumentException("A preferred window must identify one date or day of week");
		}
		if (window.dayOfWeek() != null && (window.dayOfWeek() < 1 || window.dayOfWeek() > 7)) {
			throw new IllegalArgumentException("Preferred-window day of week must be between 1 and 7");
		}
		try {
			if (window.applicableDate() != null) {
				LocalDate.parse(window.applicableDate());
			}
			LocalTime start = LocalTime.parse(window.startTime());
			LocalTime end = LocalTime.parse(window.endTime());
			if (!end.isAfter(start) || !quarterAligned(start) || !quarterAligned(end)) {
				throw new IllegalArgumentException("Preferred-window times must be ordered and 15-minute aligned");
			}
		}
		catch (DateTimeParseException | NullPointerException ex) {
			throw new IllegalArgumentException("Preferred windows must use ISO dates and times", ex);
		}
	}

	private boolean quarterAligned(LocalTime time) {
		return time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
