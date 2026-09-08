package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;

public interface SlotSuggestionPort {

	boolean placeSuggestion(SchedulingRequest request);

	boolean replaceSuggestion(SchedulingRequest request);

	SuggestionAcceptance acceptSuggestion(SchedulingRequest request);

	void releaseSuggestion(SchedulingRequest request);

	boolean placeStaffSuggestion(SchedulingRequest request, StaffSuggestionCommand command, String reason,
			String changedBy);

	boolean bookDirectly(SchedulingRequest request, StaffDirectBookingCommand command);

	enum SuggestionAcceptance {

		CONFIRMED,

		REPLACED,

		EXHAUSTED

	}

	record StaffSuggestionCommand(int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes) {

		public StaffSuggestionCommand {
			if (veterinarianId <= 0 || date == null || startTime == null || durationMinutes <= 0) {
				throw new IllegalArgumentException(
						"Staff suggestion requires a veterinarian, date, start, and duration");
			}
		}
	}

	record StaffDirectBookingCommand(int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes,
			String reason, String changedBy) {

		public StaffDirectBookingCommand {
			if (veterinarianId <= 0 || date == null || startTime == null || durationMinutes <= 0 || reason == null
					|| reason.isBlank() || changedBy == null || changedBy.isBlank()) {
				throw new IllegalArgumentException(
						"Staff direct booking requires a veterinarian, date, start, duration, reason, and actor");
			}
		}
	}

}
