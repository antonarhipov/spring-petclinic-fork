package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class SchedulingRequestPolicy {

	public void validateAvailabilityWindows(List<AvailabilityWindow> windows) {
		if (windows == null || windows.isEmpty()) {
			return;
		}

		for (AvailabilityWindow window : windows) {
			if (window.getStartTime() == null || window.getEndTime() == null) {
				throw new IllegalArgumentException("Window start and end time must be specified");
			}
			if (!window.getStartTime().isBefore(window.getEndTime())) {
				throw new IllegalArgumentException("Window start time must be before end time");
			}
			if (window.getShape() == WindowShape.ONE_OFF && window.getLocalDate() == null) {
				throw new IllegalArgumentException("ONE_OFF window requires a specific date");
			}
			if (window.getShape() == WindowShape.WEEKLY) {
				if (window.getRangeStart() == null || window.getRangeEnd() == null) {
					throw new IllegalArgumentException("WEEKLY window requires range start and end dates");
				}
				if (window.getRangeStart().isAfter(window.getRangeEnd())) {
					throw new IllegalArgumentException(
							"WEEKLY window range start must be before or equal to range end");
				}
				if (window.getWeekdays() == null || window.getWeekdays().isBlank()) {
					throw new IllegalArgumentException("WEEKLY window requires at least one weekday");
				}
			}
		}
	}

	public boolean canTransition(RequestState from, RequestState to) {
		if (from == to) {
			return true;
		}
		if (from.isTerminal()) {
			return false;
		}
		return switch (from) {
			case AWAITING_INTERPRETATION -> to == RequestState.AWAITING_REVIEW || to == RequestState.STAFF_HANDLING
					|| to == RequestState.WITHDRAWN || to == RequestState.CLOSED;
			case AWAITING_REVIEW -> to == RequestState.READY_TO_MATCH || to == RequestState.STAFF_HANDLING
					|| to == RequestState.WITHDRAWN || to == RequestState.CLOSED;
			case READY_TO_MATCH -> to == RequestState.OFFERED || to == RequestState.STAFF_HANDLING
					|| to == RequestState.WITHDRAWN || to == RequestState.CLOSED;
			case OFFERED -> to == RequestState.CONFIRMED || to == RequestState.READY_TO_MATCH
					|| to == RequestState.STAFF_HANDLING || to == RequestState.WITHDRAWN || to == RequestState.CLOSED;
			case STAFF_HANDLING -> to == RequestState.READY_TO_MATCH || to == RequestState.OFFERED
					|| to == RequestState.CONFIRMED || to == RequestState.WITHDRAWN || to == RequestState.CLOSED;
			case CONFIRMED, WITHDRAWN, CLOSED -> false;
		};
	}

}
