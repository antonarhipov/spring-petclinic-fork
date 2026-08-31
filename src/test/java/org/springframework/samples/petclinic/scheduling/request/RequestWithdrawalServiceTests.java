package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.audit.OwnerHistoryRepository;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.QueueItem;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.queue.QueueState;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RequestWithdrawalServiceTests {

	@Autowired
	private RequestWithdrawalService withdrawalService;

	@Autowired
	private SchedulingRequestService requestService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private ActiveSchedulingRequestRepository activeRequestRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private QueueItemRepository queueItemRepository;

	@Autowired
	private BackgroundJobRepository backgroundJobRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private Clock clock;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		this.backgroundJobRepository.deleteAll();
		this.queueItemRepository.deleteAll();
		this.offerRepository.deleteAll();
		this.activeRequestRepository.deleteAll();
		this.requestRepository.deleteAll();

		this.request = this.requestService.submitRequest(this.ownerId, this.petId, "Withdrawal test request", true);
	}

	@Test
	void withdrawingActiveRequestCleansUpActivePointerOffersQueueItemsAndJobs() {
		Instant now = this.clock.instant();

		WorkflowRevision wfRev = new WorkflowRevision(this.request, 1, WorkflowRevisionState.CONFIRMED, null, 30, 1,
				null, org.springframework.samples.petclinic.scheduling.interpretation.Urgency.ROUTINE, now);
		wfRev = this.workflowRevisionRepository.save(wfRev);
		this.request.setCurrentWorkflowRevision(wfRev);
		this.request = this.requestRepository.save(this.request);

		// Add an active offer
		Offer offer = new Offer(this.request, wfRev, 1, now.plus(2, ChronoUnit.DAYS),
				now.plus(2, ChronoUnit.DAYS).plus(30, ChronoUnit.MINUTES), now.plus(10, ChronoUnit.MINUTES),
				OfferOrigin.AUTOMATIC, 1);
		offer.setState(OfferState.HELD);
		offer = this.offerRepository.save(offer);

		// Add an open queue item
		QueueItem queueItem = new QueueItem(this.request, null, QueueState.NEW, "CONSENT_DECLINED",
				org.springframework.samples.petclinic.scheduling.interpretation.Urgency.ROUTINE, now);
		queueItem = this.queueItemRepository.save(queueItem);

		SchedulingRequest withdrawn = this.withdrawalService.withdrawRequest(this.request.getId(), this.ownerId,
				"Changed my mind");

		assertThat(withdrawn.getState()).isEqualTo(RequestState.WITHDRAWN);
		assertThat(withdrawn.getClosedAt()).isNotNull();

		// Active scheduling request pointer is deleted so new requests can be made
		Optional<ActiveSchedulingRequest> active = this.activeRequestRepository.findById(this.petId);
		assertThat(active).isEmpty();

		// Active hold is released
		Offer updatedOffer = this.offerRepository.findById(offer.getId()).orElseThrow();
		assertThat(updatedOffer.getState()).isEqualTo(OfferState.RELEASED);

		// Queue item is closed
		QueueItem updatedQueueItem = this.queueItemRepository.findById(queueItem.getId()).orElseThrow();
		assertThat(updatedQueueItem.getState()).isEqualTo(QueueState.CLOSED);

		// Now a new request can be submitted for the pet without conflict
		SchedulingRequest newRequest = this.requestService.submitRequest(this.ownerId, this.petId, "New request", true);
		assertThat(newRequest).isNotNull();
	}

	@Test
	void withdrawingTerminalRequestFails() {
		this.request.setState(RequestState.CONFIRMED);
		this.requestRepository.save(this.request);

		assertThatThrownBy(() -> this.withdrawalService.withdrawRequest(this.request.getId(), this.ownerId, "Reason"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot withdraw request in terminal state");
	}

	@Test
	void foreignOwnerCannotWithdrawRequest() {
		assertThatThrownBy(() -> this.withdrawalService.withdrawRequest(this.request.getId(), 999, "Reason"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Request not found");
	}

}
