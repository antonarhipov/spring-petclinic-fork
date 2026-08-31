package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.audit.AuditEventRepository;
import org.springframework.samples.petclinic.audit.OwnerHistoryRepository;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
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
class OwnerRequestRevisionServiceTests {

	@Autowired
	private OwnerRequestRevisionService revisionService;

	@Autowired
	private SchedulingRequestService requestService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private TextRevisionRepository textRevisionRepository;

	@Autowired
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Autowired
	private OfferRepository offerRepository;

	@Autowired
	private QueueItemRepository queueItemRepository;

	@Autowired
	private BackgroundJobRepository backgroundJobRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private ProtectedPayloadService payloadService;

	@Autowired
	private Clock clock;

	private final Integer ownerId = 1;

	private final Integer petId = 1;

	private SchedulingRequest request;

	@BeforeEach
	void setUp() {
		this.backgroundJobRepository.deleteAll();
		this.offerRepository.deleteAll();
		this.queueItemRepository.deleteAll();
		this.workflowRevisionRepository.deleteAll();
		this.textRevisionRepository.deleteAll();
		this.requestRepository.deleteAll();

		this.request = this.requestService.submitRequest(this.ownerId, this.petId, "Initial checkup request", true);
	}

	@Test
	void structuredRevisionUpdatesFieldsAndQueuesMatchingJobWithoutAI() {
		this.request.setState(RequestState.AWAITING_REVIEW);
		this.requestRepository.save(this.request);

		AvailabilityWindow window = new AvailabilityWindow(null, WindowClassification.PREFERRED, WindowShape.WEEKLY,
				null, java.time.LocalDate.now().plusDays(1), java.time.LocalDate.now().plusDays(10), "WEDNESDAY",
				LocalTime.of(9, 0), LocalTime.of(12, 0), "Wednesday mornings", null);
		OwnerRequestRevisionService.StructuredRevisionCommand cmd = new OwnerRequestRevisionService.StructuredRevisionCommand(
				this.request.getId(), this.ownerId, "Ear infection check", 30, 1, null, Urgency.ROUTINE,
				List.of(window));

		WorkflowRevision revision = this.revisionService.reviseStructured(cmd);

		assertThat(revision).isNotNull();
		assertThat(revision.getRevisionNumber()).isEqualTo(1);
		assertThat(this.payloadService.decryptToString(revision.getReasonPayload())).isEqualTo("Ear infection check");
		assertThat(revision.getPreferredVetId()).isEqualTo(1);
		assertThat(revision.getAvailabilityWindows()).hasSize(1);
		assertThat(revision.getAutomaticOfferCount()).isEqualTo(0);

		// Verify request state changed to READY_TO_MATCH
		SchedulingRequest updated = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.READY_TO_MATCH);

		// Verify matching job queued (NOT an interpretation job)
		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll();
		assertThat(jobs).anyMatch(j -> j.getJobType() == JobType.MATCHING && j.getState() == JobState.PENDING
				&& j.getWorkflowRevision().getId().equals(revision.getId()));
	}

	@Test
	void structuredRevisionReleasesActiveHeldOffer() {
		this.request.setState(RequestState.OFFERED);
		this.requestRepository.save(this.request);

		WorkflowRevision wfRev = new WorkflowRevision(this.request, 1, WorkflowRevisionState.CONFIRMED, null, 30, 1,
				null, Urgency.ROUTINE, this.clock.instant());
		wfRev = this.workflowRevisionRepository.save(wfRev);
		this.request.setCurrentWorkflowRevision(wfRev);
		this.request = this.requestRepository.save(this.request);

		Offer offer = new Offer(this.request, wfRev, 1, this.clock.instant().plusSeconds(3600),
				this.clock.instant().plusSeconds(5400), this.clock.instant().plusSeconds(600), OfferOrigin.AUTOMATIC,
				1);
		offer.setState(OfferState.HELD);
		this.offerRepository.save(offer);

		OwnerRequestRevisionService.StructuredRevisionCommand cmd = new OwnerRequestRevisionService.StructuredRevisionCommand(
				this.request.getId(), this.ownerId, "Updated reason", 30, null, null, Urgency.ROUTINE, List.of());

		this.revisionService.reviseStructured(cmd);

		Offer updatedOffer = this.offerRepository.findById(offer.getId()).orElseThrow();
		assertThat(updatedOffer.getState()).isEqualTo(OfferState.RELEASED);
	}

	@Test
	void proseRevisionWithConsentQueuesInterpretationJob() {
		String newProse = "Actually, Leo seems to have an ear infection on his left ear.";
		OwnerRequestRevisionService.ProseRevisionCommand cmd = new OwnerRequestRevisionService.ProseRevisionCommand(
				this.request.getId(), this.ownerId, newProse, true);

		TextRevision textRev = this.revisionService.reviseProse(cmd);

		assertThat(textRev).isNotNull();
		assertThat(textRev.getRevisionNumber()).isEqualTo(2);

		String decryptedProse = this.payloadService.decryptToString(textRev.getProsePayload());
		assertThat(decryptedProse).isEqualTo(newProse);

		// Verify interpretation job queued
		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll();
		assertThat(jobs).anyMatch(j -> j.getJobType() == JobType.INTERPRETATION && j.getState() == JobState.PENDING
				&& j.getTextRevision().getId().equals(textRev.getId()));

		SchedulingRequest updated = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.AWAITING_INTERPRETATION);
	}

	@Test
	void proseRevisionWithDeclinedConsentRoutesToStaffHandling() {
		String newProse = "I prefer staff to handle this manually now.";
		OwnerRequestRevisionService.ProseRevisionCommand cmd = new OwnerRequestRevisionService.ProseRevisionCommand(
				this.request.getId(), this.ownerId, newProse, false);

		TextRevision textRev = this.revisionService.reviseProse(cmd);

		assertThat(textRev).isNotNull();
		SchedulingRequest updated = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		// Verify no interpretation job queued for this revision
		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll()
			.stream()
			.filter(j -> j.getTextRevision() != null && j.getTextRevision().getId().equals(textRev.getId()))
			.toList();
		assertThat(jobs).isEmpty();
	}

	@Test
	void proseRevisionWithEmergencyProseRoutesToStaffHandling() {
		String newProse = "Emergency! Leo collapsed and cannot breathe!";
		OwnerRequestRevisionService.ProseRevisionCommand cmd = new OwnerRequestRevisionService.ProseRevisionCommand(
				this.request.getId(), this.ownerId, newProse, true);

		this.revisionService.reviseProse(cmd);

		SchedulingRequest updated = this.requestRepository.findById(this.request.getId()).orElseThrow();
		assertThat(updated.getState()).isEqualTo(RequestState.STAFF_HANDLING);
	}

	@Test
	void revisingTerminalRequestFails() {
		this.request.setState(RequestState.CONFIRMED);
		this.requestRepository.save(this.request);

		OwnerRequestRevisionService.StructuredRevisionCommand cmd = new OwnerRequestRevisionService.StructuredRevisionCommand(
				this.request.getId(), this.ownerId, "Reason", 30, null, null, Urgency.ROUTINE, List.of());

		assertThatThrownBy(() -> this.revisionService.reviseStructured(cmd)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot revise request in terminal state");
	}

	@Test
	void foreignOwnerCannotReviseRequest() {
		OwnerRequestRevisionService.StructuredRevisionCommand cmd = new OwnerRequestRevisionService.StructuredRevisionCommand(
				this.request.getId(), 999, "Reason", 30, null, null, Urgency.ROUTINE, List.of());

		assertThatThrownBy(() -> this.revisionService.reviseStructured(cmd))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Request not found");
	}

}
