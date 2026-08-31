package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import org.springframework.samples.petclinic.scheduling.queue.AwaitingReason;
import org.springframework.samples.petclinic.scheduling.queue.QueueItem;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;

public record OwnerRequestProjection(Long requestId, Integer petId, String petName, RequestState rawState,
		String displayState, String statusMessage, Long activeOfferId, Instant offerExpiresAt, Long appointmentId,
		Instant submittedAt, Instant updatedAt, String primaryActionLabel, String primaryActionUrl,
		boolean primaryActionPost, boolean awaitingOwnerContact) {

	public static OwnerRequestProjection from(SchedulingRequest request, String petName, Long offerId,
			Instant offerExpiresAt, boolean matchingInProgress, QueueItem queueItem) {
		boolean awaitingOwnerContact = queueItem != null && queueItem.getState() == QueueState.AWAITING_OWNER
				&& queueItem.getAwaitingReason() == AwaitingReason.CONTACT_REQUIRED;
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
			case STAFF_HANDLING ->
				awaitingOwnerContact ? "Clinic staff is waiting to hear from you. No appointment is being held."
						: "Clinic staff is reviewing your request to find the best care.";
			case CONFIRMED -> "Your appointment is confirmed.";
			case WITHDRAWN -> "This scheduling request was withdrawn.";
			case CLOSED -> "This scheduling request has been closed.";
		};
		String actionLabel = null;
		String actionUrl = null;
		boolean actionPost = false;
		switch (request.getState()) {
			case AWAITING_REVIEW -> {
				actionLabel = "Review and confirm details";
				actionUrl = "/owner/requests/" + request.getId() + "/interpretation";
			}
			case READY_TO_MATCH -> {
				if (!matchingInProgress) {
					actionLabel = "Request another option";
					actionUrl = "/owner/requests/" + request.getId() + "/match";
					actionPost = true;
				}
			}
			case OFFERED -> {
				if (offerId != null) {
					actionLabel = "Review held offer";
					actionUrl = "/owner/requests/" + request.getId() + "/offers/" + offerId;
				}
			}
			case CONFIRMED -> {
				if (request.getAppointmentId() != null) {
					actionLabel = "View appointment";
					actionUrl = "/owner/appointments/" + request.getAppointmentId();
				}
			}
			case WITHDRAWN, CLOSED -> {
				actionLabel = "Schedule another appointment";
				actionUrl = "/owner/requests/new?petId=" + request.getPetId();
			}
			default -> {
			}
		}

		return new OwnerRequestProjection(request.getId(), request.getPetId(), petName, request.getState(),
				displayState, message, offerId, offerExpiresAt, request.getAppointmentId(), request.getSubmittedAt(),
				request.getUpdatedAt(), actionLabel, actionUrl, actionPost, awaitingOwnerContact);
	}

}
