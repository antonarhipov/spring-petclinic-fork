package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerOperationStatusService {

	private final SchedulingRequestRepository requests;

	private final IntegrationExecutionRepository executions;

	private final OfferRepository offers;

	private final Clock clock;

	public OwnerOperationStatusService(SchedulingRequestRepository requests, IntegrationExecutionRepository executions,
			OfferRepository offers, Clock clock) {
		this.requests = requests;
		this.executions = executions;
		this.offers = offers;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public OwnerOperationStatus status(Integer ownerId, Long requestId, UUID operationId) {
		SchedulingRequest request = this.requests.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		IntegrationExecution execution = this.executions.findById(operationId)
			.filter(item -> item.getRequestId().equals(requestId))
			.orElseThrow(OwnerResourceNotFoundException::new);
		String state = mapState(execution, request);
		String nextUrl = nextUrl(request, execution.getId(), state);
		Integer poll = "COMPLETE".equals(state) || "ROUTED_TO_STAFF".equals(state) || "SUPERSEDED".equals(state) ? null
				: 1000;
		String kind = "TIMEFOLD_MATCH".equals(execution.getKind()) ? "MATCHING" : "INTERPRETATION";
		return new OwnerOperationStatus(execution.getId(), kind, state, statusText(state, kind),
				request.getVersion() == null ? 0 : request.getVersion(), Instant.now(this.clock), poll, nextUrl);
	}

	private String mapState(IntegrationExecution execution, SchedulingRequest request) {
		if (request.getState() == RequestState.STAFF_HANDLING) {
			return "ROUTED_TO_STAFF";
		}
		if (request.getState() == RequestState.INTERPRETATION_REVIEW
				|| request.getState() == RequestState.READY_FOR_SUGGESTION
				|| request.getState() == RequestState.OFFER_HELD
				|| request.getState() == RequestState.AWAITING_FALLBACK_CHOICE) {
			return "COMPLETE";
		}
		if ("RUNNING".equals(execution.getState())) {
			return "RUNNING";
		}
		if ("PENDING".equals(execution.getState())) {
			return "QUEUED";
		}
		if ("COMPLETE".equals(execution.getState())) {
			return "COMPLETE";
		}
		return "QUEUED";
	}

	private String nextUrl(SchedulingRequest request, UUID operationId, String state) {
		if ("COMPLETE".equals(state) && request.getState() == RequestState.INTERPRETATION_REVIEW) {
			return "/owner/scheduling-requests/" + request.getId() + "/interpretation";
		}
		if ("COMPLETE".equals(state) && request.getState() == RequestState.OFFER_HELD) {
			return this.offers
				.findFirstByRequestRevisionIdAndStatusOrderByCreatedAtDesc(request.getActiveRequestRevisionId(),
						OfferStatus.HELD)
				.map(Offer::getId)
				.map(offerId -> "/owner/scheduling-requests/" + request.getId() + "/offers/" + offerId)
				.orElse("/owner/scheduling-requests/" + request.getId() + "/status");
		}
		if ("COMPLETE".equals(state) && request.getState() == RequestState.AWAITING_FALLBACK_CHOICE) {
			return "/owner/scheduling-requests/" + request.getId() + "/fallback-choice";
		}
		if ("ROUTED_TO_STAFF".equals(state)) {
			return "/owner/scheduling-requests/" + request.getId() + "/status";
		}
		if ("QUEUED".equals(state) || "RUNNING".equals(state)) {
			return null;
		}
		return "/owner/scheduling-requests/" + request.getId() + "/processing/" + operationId;
	}

	private String statusText(String state, String kind) {
		return switch (state) {
			case "COMPLETE" -> "Your request is ready to review.";
			case "ROUTED_TO_STAFF" -> "A team member will continue this request.";
			case "RUNNING" -> "MATCHING".equals(kind) ? "We are looking for a time." : "We are reviewing your request.";
			default -> "MATCHING".equals(kind) ? "We are looking for a time." : "We are preparing your request.";
		};
	}

}
