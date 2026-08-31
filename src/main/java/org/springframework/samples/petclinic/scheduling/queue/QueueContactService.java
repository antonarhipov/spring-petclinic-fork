package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.request.ActiveSchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QueueContactService {

	private static final Logger log = LoggerFactory.getLogger(QueueContactService.class);

	private final QueueItemRepository queueItemRepository;

	private final ContactAttemptRepository contactAttemptRepository;

	private final SchedulingRequestRepository requestRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public QueueContactService(QueueItemRepository queueItemRepository,
			ContactAttemptRepository contactAttemptRepository, SchedulingRequestRepository requestRepository,
			ActiveSchedulingRequestRepository activeRequestRepository, ProtectedPayloadService payloadService,
			AuditService auditService, OwnerHistoryService ownerHistoryService, Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.contactAttemptRepository = contactAttemptRepository;
		this.requestRepository = requestRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.payloadService = payloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public ContactAttempt recordContactAttempt(Long queueItemId, Long actorAccountId, ContactOutcome outcome,
			String note) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");
		Objects.requireNonNull(actorAccountId, "actorAccountId must not be null");
		Objects.requireNonNull(outcome, "outcome must not be null");

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException(
					"Cannot log contact attempt on inactive queue item: " + queueItem.getState());
		}

		Instant now = this.clock.instant();
		ProtectedPayload notePayload = null;
		if (note != null && !note.isBlank()) {
			notePayload = this.payloadService.store(UUID.randomUUID(), "CONTACT_NOTE", 1, "text/plain", note.trim());
		}

		ContactAttempt attempt = new ContactAttempt(queueItem, actorAccountId, now, outcome, notePayload, now);
		ContactAttempt savedAttempt = this.contactAttemptRepository.save(attempt);

		queueItem.setLastContactAt(now);
		if (outcome == ContactOutcome.UNREACHABLE || outcome == ContactOutcome.LEFT_VOICEMAIL) {
			queueItem.setState(QueueState.AWAITING_OWNER);
			queueItem.setAwaitingReason(AwaitingReason.CONTACT_REQUIRED);
		}
		queueItem.setUpdatedAt(now);
		this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(actorAccountId, "CONTACT_ATTEMPT_RECORDED", "QueueItem", queueItemId.toString(),
				"SUCCESS", UUID.randomUUID(), null, notePayload != null ? notePayload.getId() : null);

		SchedulingRequest req = queueItem.getRequest();
		this.ownerHistoryService.recordOwnerHistory(req.getOwnerId(), req.getPetId(), req.getId(),
				"STAFF_CONTACT_ATTEMPT", "Clinic staff recorded contact attempt: " + outcome, null);

		log.info("Recorded contact attempt {} for queue item {} with outcome {}", savedAttempt.getId(), queueItemId,
				outcome);
		return savedAttempt;
	}

	public QueueItem closeQueueItem(Long queueItemId, Long actorAccountId, String reason) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Closure reason is required");
		}

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		Instant now = this.clock.instant();
		ProtectedPayload reasonPayload = this.payloadService.store(UUID.randomUUID(), "QUEUE_CLOSURE_REASON", 1,
				"text/plain", reason.trim());

		queueItem.setState(QueueState.CLOSED);
		queueItem.setClosedAt(now);
		queueItem.setUpdatedAt(now);
		QueueItem saved = this.queueItemRepository.save(queueItem);

		SchedulingRequest request = queueItem.getRequest();
		if (request.getState() != RequestState.CONFIRMED && request.getState() != RequestState.CLOSED
				&& request.getState() != RequestState.WITHDRAWN) {
			request.setState(RequestState.CLOSED);
			request.setClosedAt(now);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);
			this.activeRequestRepository.deleteByRequestId(request.getId());
		}

		this.auditService.recordEvent(actorAccountId, "QUEUE_ITEM_CLOSED", "QueueItem", queueItemId.toString(),
				"SUCCESS", UUID.randomUUID(), null, reasonPayload.getId());

		this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
				"REQUEST_CLOSED", "Request closed by clinic staff: " + reason.trim(), null);

		log.info("Queue item {} closed by staff {} with reason: {}", queueItemId, actorAccountId, reason);
		return saved;
	}

}
