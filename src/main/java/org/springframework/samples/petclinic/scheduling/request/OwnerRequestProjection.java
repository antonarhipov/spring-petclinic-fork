package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

public record OwnerRequestProjection(Long requestId, Integer petId, String petName, RequestState rawState,
		String displayState, String statusMessage, Long activeOfferId, Instant offerExpiresAt, Long appointmentId,
		Instant submittedAt, Instant updatedAt) {

	public static OwnerRequestProjection from(SchedulingRequest request, String petName, Long offerId,
			Instant offerExpiresAt) {
		String displayState = switch (request.getState()) {
			case AWAITING_INTERPRETATION -> "INTERPRETING_REQUEST";
			case AWAITING_REVIEW -> "REVIEW_INTERPRETATION";
			case READY_TO_MATCH -> "FINDING_APPOINTMENT";
			case OFFERED -> "APPOINTMENT_OFFERED";
			case STAFF_HANDLING -> "WITH_CLINIC_STAFF";
			case CONFIRMED -> "CONFIRMED";
			case WITHDRAWN -> "WITHDRAWN";
			case CLOSED -> "CLOSED";
		};

		String message = switch (request.getState()) {
			case AWAITING_INTERPRETATION -> "We are interpreting your scheduling request with AI...";
			case AWAITING_REVIEW -> "Your request details are ready for your review and confirmation.";
			case READY_TO_MATCH -> "Searching for the best appointment slot based on your preferences...";
			case OFFERED -> "An appointment offer is currently held for your acceptance.";
			case STAFF_HANDLING -> "Clinic staff is reviewing your request to find the best care.";
			case CONFIRMED -> "Your appointment is confirmed.";
			case WITHDRAWN -> "This scheduling request was withdrawn.";
			case CLOSED -> "This scheduling request has been closed.";
		};

		return new OwnerRequestProjection(request.getId(), request.getPetId(), petName, request.getState(),
				displayState, message, offerId, offerExpiresAt, request.getAppointmentId(), request.getSubmittedAt(),
				request.getUpdatedAt());
	}

}
