package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.queue.QueueItem;
import org.springframework.samples.petclinic.scheduling.queue.QueueItemRepository;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusion;
import org.springframework.samples.petclinic.scheduling.request.OfferExclusionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestService;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OfferLifecycleServiceTests {

	@Autowired
	private OfferLifecycleService offerLifecycleService;

	@Autowired
	private OfferExpiryWorker offerExpiryWorker;

	@Autowired
	private SchedulingRequestService requestService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private OfferExclusionRepository exclusionRepository;

	@Autowired
	private QueueItemRepository queueItemRepository;

	@Autowired
	private BackgroundJobRepository backgroundJobRepository;

	@Autowired
	private Clock clock;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private SchedulingRequest request;

	private WorkflowRevision workflowRevision;

	@BeforeEach
	void setUp() {
		this.backgroundJobRepository.deleteAll();
		this.queueItemRepository.deleteAll();
		this.exclusionRepository.deleteAll();
		this.offerRepository.deleteAll();
		this.workflowRevisionRepository.deleteAll();
		this.requestRepository.deleteAll();

		this.request = this.requestService.submitRequest(this.ownerId, this.petId, "Routine checkup", true);

		this.workflowRevision = new WorkflowRevision(this.request, 1,
				org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState.CONFIRMED, null, 30, 1,
				null, org.springframework.samples.petclinic.scheduling.interpretation.Urgency.ROUTINE,
				this.clock.instant());
		this.workflowRevision.setAutomaticOfferCount(1);
		this.workflowRevision = this.workflowRevisionRepository.save(this.workflowRevision);

		this.request.setCurrentWorkflowRevision(this.workflowRevision);
		this.request.setState(RequestState.OFFERED);
		this.request = this.requestRepository.save(this.request);
	}

	@Test
	void rejectingOfferRecordsExclusionAndSetsRequestToReadyToMatch() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(2, ChronoUnit.DAYS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);
		Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

		Offer offer = new Offer(this.request, this.workflowRevision, 1, startAt, endAt, expiresAt,
				OfferOrigin.AUTOMATIC, 1);
		offer.setState(OfferState.HELD);
		offer = this.offerRepository.save(offer);

		Offer rejected = this.offerLifecycleService.rejectOffer(offer.getId(), this.ownerId, "Not a good time");

		assertThat(rejected.getState()).isEqualTo(OfferState.REJECTED);

		// Verify exclusion created for the exact slot
		List<OfferExclusion> exclusions = this.exclusionRepository
			.findByWorkflowRevisionId(this.workflowRevision.getId());
		assertThat(exclusions).hasSize(1);
		assertThat(exclusions.get(0).getVetId()).isEqualTo(1);
		assertThat(exclusions.get(0).getStartAt()).isEqualTo(startAt);

		// Verify request state
		SchedulingRequest updatedReq = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updatedReq.getState()).isEqualTo(RequestState.READY_TO_MATCH);
	}

	@Test
	void requestingNextOfferQueuesMatchingJob() {
		this.request.setState(RequestState.READY_TO_MATCH);
		this.requestRepository.save(this.request);

		SchedulingRequest result = this.offerLifecycleService.requestNextOffer(this.request.getId(), this.ownerId);

		assertThat(result.getState()).isEqualTo(RequestState.READY_TO_MATCH);

		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll();
		assertThat(jobs).anyMatch(j -> j.getJobType() == JobType.MATCHING && j.getState() == JobState.PENDING
				&& j.getWorkflowRevision().getId().equals(this.workflowRevision.getId()));
	}

	@Test
	void rejectingFifthOfferRoutesToStaffFallbackQueue() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(2, ChronoUnit.DAYS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);
		Instant expiresAt = now.plus(10, ChronoUnit.MINUTES);

		Offer offer = new Offer(this.request, this.workflowRevision, 1, startAt, endAt, expiresAt,
				OfferOrigin.AUTOMATIC, 5);
		offer.setState(OfferState.HELD);
		offer = this.offerRepository.save(offer);

		this.workflowRevision.setAutomaticOfferCount(5);
		this.workflowRevisionRepository.save(this.workflowRevision);

		this.offerLifecycleService.rejectOffer(offer.getId(), this.ownerId, "5th offer rejected");

		SchedulingRequest updatedReq = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updatedReq.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		// Verify queue item created
		List<QueueItem> queueItems = this.queueItemRepository.findAll();
		assertThat(queueItems).anyMatch(q -> q.getRequest().getId().equals(this.request.getId())
				&& q.getFallbackReason().equals("MAX_OFFERS_EXCEEDED"));
	}

	@Test
	void expiringOfferViaWorkerUpdatesStateAndExclusion() {
		Instant now = this.clock.instant();
		Instant startAt = now.plus(2, ChronoUnit.DAYS);
		Instant endAt = startAt.plus(30, ChronoUnit.MINUTES);
		Instant expiresAt = now.minus(1, ChronoUnit.MINUTES); // Already expired

		Offer offer = new Offer(this.request, this.workflowRevision, 1, startAt, endAt, expiresAt,
				OfferOrigin.AUTOMATIC, 1);
		offer.setState(OfferState.HELD);
		this.offerRepository.save(offer);

		int expiredCount = this.offerExpiryWorker.checkAndExpireOffers();

		assertThat(expiredCount).isEqualTo(1);
		Offer updatedOffer = this.offerRepository.findById(offer.getId()).orElseThrow();
		assertThat(updatedOffer.getState()).isEqualTo(OfferState.EXPIRED);

		List<OfferExclusion> exclusions = this.exclusionRepository
			.findByWorkflowRevisionId(this.workflowRevision.getId());
		assertThat(exclusions).hasSize(1);
	}

}
