package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class FallbackService implements StaffFallbackPort {

	private static final Logger log = LoggerFactory.getLogger(FallbackService.class);

	private final SchedulingRequestRepository requestRepository;

	private final QueueItemRepository queueItemRepository;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public FallbackService(SchedulingRequestRepository requestRepository, QueueItemRepository queueItemRepository,
			AuditService auditService, OwnerHistoryService ownerHistoryService, Clock clock) {
		this.requestRepository = requestRepository;
		this.queueItemRepository = queueItemRepository;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void sendToFallbackQueue(Long requestId, String fallbackReason, Urgency urgency, String note) {
		Instant now = this.clock.instant();
		log.info("Routing request {} to staff fallback queue. Reason: {}, Urgency: {}, Note: {}", requestId,
				fallbackReason, urgency, note);

		SchedulingRequest request = this.requestRepository.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("Scheduling request not found: " + requestId));

		request.setState(RequestState.STAFF_HANDLING);
		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		Optional<QueueItem> existingItem = this.queueItemRepository.findByRequestId(requestId);
		QueueItem queueItem;
		if (existingItem.isPresent()) {
			queueItem = existingItem.get();
			queueItem.setState(QueueState.NEW);
			queueItem.setFallbackReason(fallbackReason);
			queueItem.setUrgency(urgency);
			if (request.getCurrentWorkflowRevision() != null) {
				queueItem.setWorkflowRevision(request.getCurrentWorkflowRevision());
			}
			queueItem.setUpdatedAt(now);
			queueItem = this.queueItemRepository.save(queueItem);
		}
		else {
			queueItem = new QueueItem(request, request.getCurrentWorkflowRevision(), QueueState.NEW, fallbackReason,
					urgency, now);
			queueItem = this.queueItemRepository.save(queueItem);
		}

		this.auditService.recordEvent(null, "REQUEST_ROUTED_TO_FALLBACK", "SchedulingRequest", requestId.toString(),
				"SUCCESS", UUID.randomUUID(), null, null);

		this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
				"REQUEST_ROUTED_TO_STAFF", "Request routed to clinic staff for assistance: " + fallbackReason, null);
	}

}
