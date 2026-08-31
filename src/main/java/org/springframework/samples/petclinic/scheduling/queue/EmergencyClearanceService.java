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
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmergencyClearanceService {

	private static final Logger log = LoggerFactory.getLogger(EmergencyClearanceService.class);

	private final QueueItemRepository queueItemRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final ProtectedPayloadService payloadService;

	private final AuditService auditService;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public EmergencyClearanceService(QueueItemRepository queueItemRepository,
			SchedulingRequestRepository requestRepository, WorkflowRevisionRepository workflowRevisionRepository,
			ProtectedPayloadService payloadService, AuditService auditService, OwnerHistoryService ownerHistoryService,
			Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.payloadService = payloadService;
		this.auditService = auditService;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	public WorkflowRevision clearEmergency(Long queueItemId, Long actorAccountId, Urgency newUrgency,
			String clinicalJustification) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");
		Objects.requireNonNull(actorAccountId, "actorAccountId must not be null");
		Objects.requireNonNull(newUrgency, "newUrgency must not be null");

		if (newUrgency == Urgency.EMERGENCY_SUSPECTED) {
			throw new IllegalArgumentException("Cleared urgency must be ROUTINE or PRIORITY");
		}
		if (clinicalJustification == null || clinicalJustification.isBlank()) {
			throw new IllegalArgumentException("Clinical justification is required for emergency clearance");
		}

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException(
					"Cannot clear emergency on inactive queue item in state: " + queueItem.getState());
		}

		Instant now = this.clock.instant();
		ProtectedPayload justificationPayload = this.payloadService.store(UUID.randomUUID(),
				"EMERGENCY_CLEARANCE_JUSTIFICATION", 1, "text/plain", clinicalJustification.trim());

		SchedulingRequest request = queueItem.getRequest();
		WorkflowRevision priorRevision = request.getCurrentWorkflowRevision();
		if (priorRevision != null) {
			priorRevision.setState(WorkflowRevisionState.SUPERSEDED);
			this.workflowRevisionRepository.save(priorRevision);
		}

		int nextRevisionNumber = priorRevision != null ? priorRevision.getRevisionNumber() + 1 : 1;
		WorkflowRevision newRevision = new WorkflowRevision(request, nextRevisionNumber,
				WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED,
				priorRevision != null ? priorRevision.getReasonPayload() : null,
				priorRevision != null ? priorRevision.getDurationMinutes() : 30,
				priorRevision != null ? priorRevision.getPreferredVetId() : null,
				priorRevision != null ? priorRevision.getRequiredSpecialtyId() : null, newUrgency, now);

		if (priorRevision != null) {
			for (AvailabilityWindow window : priorRevision.getAvailabilityWindows()) {
				AvailabilityWindow copyWindow = new AvailabilityWindow(newRevision, window.getClassification(),
						window.getShape(), window.getLocalDate(), window.getRangeStart(), window.getRangeEnd(),
						window.getWeekdays(), window.getStartTime(), window.getEndTime(),
						window.getSourceText() != null ? window.getSourceText() : "Cleared emergency",
						window.getResolutionNote());
				newRevision.addAvailabilityWindow(copyWindow);
			}
		}

		WorkflowRevision savedRevision = this.workflowRevisionRepository.save(newRevision);

		request.setCurrentWorkflowRevision(savedRevision);
		request.setState(RequestState.AWAITING_REVIEW);
		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		queueItem.setWorkflowRevision(savedRevision);
		queueItem.setUrgency(newUrgency);
		queueItem.setState(QueueState.AWAITING_OWNER);
		queueItem.setAwaitingReason(AwaitingReason.INTERPRETATION_CONFIRMATION);
		queueItem.setUpdatedAt(now);
		this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(actorAccountId, "EMERGENCY_CLEARED", "QueueItem", queueItem.getId().toString(),
				"SUCCESS", UUID.randomUUID(), null, justificationPayload.getId());

		this.ownerHistoryService.recordOwnerHistory(request.getOwnerId(), request.getPetId(), request.getId(),
				"EMERGENCY_CLEARED", "Urgent safety flag cleared by clinic staff. Urgency updated to: " + newUrgency,
				null);

		log.info("Cleared emergency for queue item {} (request {}). New urgency: {}", queueItemId, request.getId(),
				newUrgency);
		return savedRevision;
	}

}
