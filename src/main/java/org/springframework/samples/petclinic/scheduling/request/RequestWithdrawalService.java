package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.availability.CalendarMutationCoordinator;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RequestWithdrawalService {

	private static final Logger log = LoggerFactory.getLogger(RequestWithdrawalService.class);

	private final SchedulingRequestRepository requestRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final OfferRepository offerRepository;

	private final QueueItemRepository queueItemRepository;

	private final BackgroundJobRepository jobRepository;

	private final CalendarMutationCoordinator calendarCoordinator;

	private final OwnerHistoryService ownerHistoryService;

	private final AuditService auditService;

	private final Clock clock;

	public RequestWithdrawalService(SchedulingRequestRepository requestRepository,
			ActiveSchedulingRequestRepository activeRequestRepository, OfferRepository offerRepository,
			QueueItemRepository queueItemRepository, BackgroundJobRepository jobRepository,
			CalendarMutationCoordinator calendarCoordinator, OwnerHistoryService ownerHistoryService,
			AuditService auditService, Clock clock) {
		this.requestRepository = requestRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.offerRepository = offerRepository;
		this.queueItemRepository = queueItemRepository;
		this.jobRepository = jobRepository;
		this.calendarCoordinator = calendarCoordinator;
		this.ownerHistoryService = ownerHistoryService;
		this.auditService = auditService;
		this.clock = clock;
	}

	public SchedulingRequest withdrawRequest(Long requestId, Integer ownerId, String reason) {
		Objects.requireNonNull(requestId, "requestId must not be null");
		Objects.requireNonNull(ownerId, "ownerId must not be null");

		return this.calendarCoordinator.executeWithLock(() -> {
			SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
				.orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));

			if (request.getState().isTerminal()) {
				throw new IllegalStateException("Cannot withdraw request in terminal state: " + request.getState());
			}

			Instant now = this.clock.instant();

			// Release active held offers
			this.offerRepository.findAllByRequestId(requestId).forEach(offer -> {
				if (offer.getState() == OfferState.HELD) {
					offer.setState(OfferState.RELEASED);
					this.offerRepository.save(offer);
				}
			});

			// Close any queue item
			this.queueItemRepository.findByRequestId(requestId).ifPresent(queueItem -> {
				if (queueItem.getState() != QueueState.RESOLVED && queueItem.getState() != QueueState.CLOSED) {
					queueItem.setState(QueueState.CLOSED);
					queueItem.setClosedAt(now);
					queueItem.setUpdatedAt(now);
					this.queueItemRepository.save(queueItem);
				}
			});

			// Invalidate pending background jobs
			this.jobRepository.findAll().forEach(job -> {
				if (job.getState() == JobState.PENDING) {
					if (job.getTextRevision() != null && job.getTextRevision().getRequest().getId().equals(requestId)) {
						job.setState(JobState.FAILED);
						this.jobRepository.save(job);
					}
					else if (job.getWorkflowRevision() != null
							&& job.getWorkflowRevision().getRequest().getId().equals(requestId)) {
						job.setState(JobState.FAILED);
						this.jobRepository.save(job);
					}
				}
			});

			request.setState(RequestState.WITHDRAWN);
			request.setClosedAt(now);
			request.setUpdatedAt(now);
			this.requestRepository.save(request);

			this.activeRequestRepository.deleteByRequestId(requestId);

			this.ownerHistoryService.recordOwnerHistory(ownerId, request.getPetId(), requestId, "REQUEST_WITHDRAWN",
					"Request withdrawn by owner" + (reason != null && !reason.isBlank() ? ": " + reason.trim() : ""),
					null);

			log.info("Owner {} withdrew scheduling request {}", ownerId, requestId);
			return request;
		});
	}

}
