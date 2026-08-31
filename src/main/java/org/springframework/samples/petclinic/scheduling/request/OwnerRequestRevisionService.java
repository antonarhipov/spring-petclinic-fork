package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyKeywordScreen;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerRequestRevisionService {

	private static final Logger log = LoggerFactory.getLogger(OwnerRequestRevisionService.class);

	private final SchedulingRequestRepository requestRepository;

	private final TextRevisionRepository textRevisionRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final BackgroundJobRepository jobRepository;

	private final OfferRepository offerRepository;

	private final QueueItemRepository queueItemRepository;

	private final ProtectedPayloadService payloadService;

	private final EmergencyKeywordScreen emergencyScreen;

	private final StaffFallbackPort staffFallbackPort;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final SchedulingRequestPolicy policy;

	private final Clock clock;

	public OwnerRequestRevisionService(SchedulingRequestRepository requestRepository,
			TextRevisionRepository textRevisionRepository, WorkflowRevisionRepository workflowRevisionRepository,
			BackgroundJobRepository jobRepository, OfferRepository offerRepository,
			QueueItemRepository queueItemRepository, ProtectedPayloadService payloadService,
			EmergencyKeywordScreen emergencyScreen, StaffFallbackPort staffFallbackPort,
			OwnerHistoryService ownerHistoryService, AuditService auditService, SchedulingRequestPolicy policy,
			Clock clock) {
		this.requestRepository = requestRepository;
		this.textRevisionRepository = textRevisionRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.jobRepository = jobRepository;
		this.offerRepository = offerRepository;
		this.queueItemRepository = queueItemRepository;
		this.payloadService = payloadService;
		this.emergencyScreen = emergencyScreen;
		this.staffFallbackPort = staffFallbackPort;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.policy = policy;
		this.clock = clock;
	}

	public record StructuredRevisionCommand(Long requestId, Integer ownerId, String visitReason,
			Integer durationMinutes, Integer preferredVetId, Integer requiredSpecialtyId, Urgency urgency,
			List<AvailabilityWindow> windows) {
	}

	public record ProseRevisionCommand(Long requestId, Integer ownerId, String prose, boolean aiConsent) {
	}

	public WorkflowRevision reviseStructured(StructuredRevisionCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.requestId(), "requestId must not be null");
		Objects.requireNonNull(cmd.ownerId(), "ownerId must not be null");

		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(cmd.requestId(), cmd.ownerId())
			.orElseThrow(() -> new IllegalArgumentException("Request not found: " + cmd.requestId()));

		if (request.getState().isTerminal()) {
			throw new IllegalStateException("Cannot revise request in terminal state: " + request.getState());
		}

		if (cmd.windows() != null) {
			this.policy.validateAvailabilityWindows(cmd.windows());
		}

		Instant now = this.clock.instant();

		// Release any active held offer
		this.offerRepository.findAllByRequestId(request.getId()).forEach(offer -> {
			if (offer.getState() == OfferState.HELD) {
				offer.setState(OfferState.RELEASED);
				this.offerRepository.save(offer);
			}
		});

		// Supersede pending jobs for previous revision
		this.jobRepository.findAll().forEach(job -> {
			if (job.getWorkflowRevision() != null
					&& job.getWorkflowRevision().getRequest().getId().equals(request.getId())
					&& job.getState() == JobState.PENDING) {
				job.setState(JobState.FAILED);
				this.jobRepository.save(job);
			}
		});

		WorkflowRevision priorRevision = request.getCurrentWorkflowRevision();
		String priorRevisionState = priorRevision != null ? priorRevision.getState().name() : "NONE";
		if (priorRevision != null) {
			priorRevision.setState(WorkflowRevisionState.SUPERSEDED);
			this.workflowRevisionRepository.save(priorRevision);
		}

		ProtectedPayload reasonPayload = null;
		if (cmd.visitReason() != null && !cmd.visitReason().isBlank()) {
			reasonPayload = this.payloadService.store(UUID.randomUUID(), "VISIT_REASON", 1, "text/plain",
					cmd.visitReason().trim());
		}
		else if (priorRevision != null) {
			reasonPayload = priorRevision.getReasonPayload();
		}

		int nextRevisionNumber = priorRevision != null ? priorRevision.getRevisionNumber() + 1 : 1;
		Urgency urgency = priorRevision != null ? priorRevision.getUrgency() : Urgency.ROUTINE;
		Integer requiredSpecialtyId = priorRevision != null ? priorRevision.getRequiredSpecialtyId() : null;
		Integer duration = (cmd.durationMinutes() != null) ? cmd.durationMinutes()
				: (priorRevision != null ? priorRevision.getDurationMinutes() : 30);

		WorkflowRevision newRevision = new WorkflowRevision(request, nextRevisionNumber,
				WorkflowRevisionState.CONFIRMED, reasonPayload, duration, cmd.preferredVetId(), requiredSpecialtyId,
				urgency, now);
		newRevision.setConfirmedAt(now);
		newRevision.setAutomaticOfferCount(0);

		if (cmd.windows() != null) {
			for (AvailabilityWindow window : cmd.windows()) {
				newRevision.addAvailabilityWindow(window);
			}
		}

		WorkflowRevision savedRevision = this.workflowRevisionRepository.save(newRevision);
		request.setCurrentWorkflowRevision(savedRevision);

		if (urgency == Urgency.ROUTINE) {
			request.setState(RequestState.READY_TO_MATCH);
			this.queueItemRepository.findByRequestId(request.getId()).ifPresent(q -> {
				q.setState(QueueState.NEW);
				q.setAssigneeAccountId(null);
				q.setAwaitingReason(null);
				q.setWorkflowRevision(savedRevision);
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			});

			BackgroundJob job = new BackgroundJob(JobType.MATCHING, null, savedRevision, JobState.PENDING, now);
			this.jobRepository.save(job);
		}
		else {
			request.setState(RequestState.STAFF_HANDLING);
			this.queueItemRepository.findByRequestId(request.getId()).ifPresentOrElse(q -> {
				q.setState(QueueState.NEW);
				q.setAssigneeAccountId(null);
				q.setAwaitingReason(null);
				q.setUrgency(urgency);
				q.setWorkflowRevision(savedRevision);
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			}, () -> {
				this.staffFallbackPort.sendToFallbackQueue(request.getId(), "URGENCY_" + urgency, urgency,
						"Owner revised structured details with non-routine urgency");
			});
		}

		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		this.ownerHistoryService.recordOwnerHistory(cmd.ownerId(), request.getPetId(), request.getId(),
				"STRUCTURED_REVISED", "Structured appointment preferences updated by owner", null);
		this.auditService.recordStructuredEvent(null, "OWNER_STRUCTURED_REVISION_CREATED", "SchedulingRequest",
				request.getId().toString(), "SUCCESS", null, null,
				Map.of("state", priorRevisionState, "revisionId", priorRevision != null ? priorRevision.getId() : 0),
				Map.of("state", request.getState().name(), "revisionId", savedRevision.getId(), "revisionNumber",
						savedRevision.getRevisionNumber()));

		log.info("Owner {} revised structured details for request {} (new revision {})", cmd.ownerId(), request.getId(),
				savedRevision.getId());
		return savedRevision;
	}

	public TextRevision reviseProse(ProseRevisionCommand cmd) {
		Objects.requireNonNull(cmd, "cmd must not be null");
		Objects.requireNonNull(cmd.requestId(), "requestId must not be null");
		Objects.requireNonNull(cmd.ownerId(), "ownerId must not be null");

		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(cmd.requestId(), cmd.ownerId())
			.orElseThrow(() -> new IllegalArgumentException("Request not found: " + cmd.requestId()));
		RequestState priorRequestState = request.getState();

		if (request.getState().isTerminal()) {
			throw new IllegalStateException("Cannot revise request in terminal state: " + request.getState());
		}

		SchedulingProsePolicy.validate(cmd.prose());

		Instant now = this.clock.instant();

		// Release any active held offer
		this.offerRepository.findAllByRequestId(request.getId()).forEach(offer -> {
			if (offer.getState() == OfferState.HELD) {
				offer.setState(OfferState.RELEASED);
				this.offerRepository.save(offer);
			}
		});

		// Invalidate pending background jobs
		this.jobRepository.findAll().forEach(job -> {
			if (job.getTextRevision() != null && job.getTextRevision().getRequest().getId().equals(request.getId())
					&& job.getState() == JobState.PENDING) {
				job.setState(JobState.FAILED);
				this.jobRepository.save(job);
			}
		});

		TextRevision priorTextRev = request.getCurrentTextRevision();
		int nextTextRevisionNumber = (priorTextRev != null) ? priorTextRev.getRevisionNumber() + 1 : 1;

		ProtectedPayload prosePayload = this.payloadService.encrypt(cmd.prose());
		ProtectedPayload consentPayload = this.payloadService
			.encrypt("{\"consented\":" + cmd.aiConsent() + ",\"timestamp\":\"" + now + "\"}");

		TextRevision textRevision = new TextRevision(request, nextTextRevisionNumber, now, prosePayload,
				consentPayload);
		TextRevision savedTextRevision = this.textRevisionRepository.save(textRevision);
		request.setCurrentTextRevision(savedTextRevision);

		EmergencyKeywordScreen.EmergencyScreenResult screenResult = this.emergencyScreen.screen(cmd.prose());
		this.auditService.recordEvent(null, "EMERGENCY_SCREEN", "TextRevision", savedTextRevision.getId().toString(),
				screenResult.emergencyDetected() ? "MATCH" : "CLEAR", null, null, null);

		if (screenResult.emergencyDetected()) {
			request.setState(RequestState.STAFF_HANDLING);
			this.queueItemRepository.findByRequestId(request.getId()).ifPresentOrElse(q -> {
				q.setState(QueueState.NEW);
				q.setAssigneeAccountId(null);
				q.setAwaitingReason(null);
				q.setUrgency(Urgency.EMERGENCY_SUSPECTED);
				q.setFallbackReason("EMERGENCY_KEYWORD_DETECTED");
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			}, () -> {
				this.staffFallbackPort.sendToFallbackQueue(request.getId(), "EMERGENCY_KEYWORD_DETECTED",
						Urgency.EMERGENCY_SUSPECTED, "Emergency keywords detected in revised prose");
			});
		}
		else if (cmd.aiConsent()) {
			request.setState(RequestState.AWAITING_INTERPRETATION);
			this.queueItemRepository.findByRequestId(request.getId()).ifPresent(q -> {
				q.setState(QueueState.NEW);
				q.setAssigneeAccountId(null);
				q.setAwaitingReason(null);
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			});

			BackgroundJob job = new BackgroundJob(JobType.INTERPRETATION, savedTextRevision, null, JobState.PENDING,
					now);
			this.jobRepository.save(job);
		}
		else {
			request.setState(RequestState.STAFF_HANDLING);
			this.queueItemRepository.findByRequestId(request.getId()).ifPresentOrElse(q -> {
				q.setState(QueueState.NEW);
				q.setAssigneeAccountId(null);
				q.setAwaitingReason(null);
				q.setUrgency(Urgency.ROUTINE);
				q.setFallbackReason("DECLINED_AI_CONSENT");
				q.setUpdatedAt(now);
				this.queueItemRepository.save(q);
			}, () -> {
				this.staffFallbackPort.sendToFallbackQueue(request.getId(), "DECLINED_AI_CONSENT", Urgency.ROUTINE,
						"Owner declined AI consent on prose revision");
			});
		}

		request.setUpdatedAt(now);
		this.requestRepository.save(request);

		this.ownerHistoryService.recordOwnerHistory(cmd.ownerId(), request.getPetId(), request.getId(), "PROSE_REVISED",
				"New request prose submitted (AI consent: " + cmd.aiConsent() + ")", null);
		this.auditService.recordStructuredEvent(null, "OWNER_PROSE_REVISION_CREATED", "SchedulingRequest",
				request.getId().toString(), "SUCCESS", null, null,
				Map.of("state", priorRequestState.name(), "textRevisionId",
						priorTextRev != null ? priorTextRev.getId() : 0),
				Map.of("state", request.getState().name(), "textRevisionId", savedTextRevision.getId(), "aiConsent",
						cmd.aiConsent()));

		log.info("Owner {} revised prose for request {} (new text revision {})", cmd.ownerId(), request.getId(),
				savedTextRevision.getId());
		return savedTextRevision;
	}

}
