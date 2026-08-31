package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchedulingRequestServiceTests {

	@Autowired
	private SchedulingRequestService requestService;

	@Autowired
	private SchedulingRequestRepository requestRepository;

	@Autowired
	private ActiveSchedulingRequestRepository activeSchedulingRequestRepository;

	@Autowired
	private TextRevisionRepository textRevisionRepository;

	@Autowired
	private BackgroundJobRepository backgroundJobRepository;

	@Autowired
	private AuditEventRepository auditEventRepository;

	@Autowired
	private OwnerHistoryRepository ownerHistoryRepository;

	@Autowired
	private ProtectedPayloadService payloadService;

	private final Integer ownerId = 1; // George Franklin

	private final Integer petId = 1; // Leo (owned by George)

	private final Integer otherOwnerPetId = 2; // Basil (owned by Betty Davis)

	@BeforeEach
	void setUp() {
		this.backgroundJobRepository.deleteAll();
		this.activeSchedulingRequestRepository.deleteAll();
		this.textRevisionRepository.deleteAll();
		this.requestRepository.deleteAll();
	}

	@Test
	void submittingRoutineConsentedRequestCreatesRequestAndInterpretationJob() {
		String prose = "Leo needs a routine checkup next Monday morning.";
		SchedulingRequest request = this.requestService.submitRequest(this.ownerId, this.petId, prose, true);

		assertThat(request).isNotNull();
		assertThat(request.getId()).isNotNull();
		assertThat(request.getOwnerId()).isEqualTo(this.ownerId);
		assertThat(request.getPetId()).isEqualTo(this.petId);
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_INTERPRETATION);

		// Verify active request pointer
		Optional<ActiveSchedulingRequest> active = this.activeSchedulingRequestRepository.findById(this.petId);
		assertThat(active).isPresent();
		assertThat(active.get().getRequestId()).isEqualTo(request.getId());

		// Verify text revision and payload encryption
		List<TextRevision> revisions = this.textRevisionRepository
			.findByRequestIdOrderByRevisionNumberAsc(request.getId());
		assertThat(revisions).hasSize(1);
		TextRevision textRev = revisions.get(0);
		String decryptedConsent = this.payloadService.decryptToString(textRev.getConsentPayload());
		assertThat(decryptedConsent).contains("true");
		String decryptedProse = this.payloadService.decryptToString(textRev.getProsePayload());
		assertThat(decryptedProse).isEqualTo(prose);

		// Verify background job queued for interpretation
		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll()
			.stream()
			.filter(j -> j.getTextRevision() != null && j.getTextRevision().getId().equals(textRev.getId()))
			.toList();
		assertThat(jobs).hasSize(1);
		BackgroundJob job = jobs.get(0);
		assertThat(job.getJobType()).isEqualTo(JobType.INTERPRETATION);
		assertThat(job.getState()).isEqualTo(JobState.PENDING);
	}

	@Test
	void submittingRequestWithDeclinedConsentRoutesToStaffHandlingWithoutAIJob() {
		String prose = "Leo needs vaccination, I do not want AI parsing.";
		SchedulingRequest request = this.requestService.submitRequest(this.ownerId, this.petId, prose, false);

		assertThat(request).isNotNull();
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);

		List<TextRevision> revisions = this.textRevisionRepository
			.findByRequestIdOrderByRevisionNumberAsc(request.getId());
		assertThat(revisions).hasSize(1);
		TextRevision textRev = revisions.get(0);

		// Verify no background interpretation job created
		List<BackgroundJob> jobs = this.backgroundJobRepository.findAll()
			.stream()
			.filter(j -> j.getTextRevision() != null && j.getTextRevision().getId().equals(textRev.getId()))
			.toList();
		assertThat(jobs).isEmpty();

		String decryptedConsent = this.payloadService.decryptToString(textRev.getConsentPayload());
		assertThat(decryptedConsent).contains("false");
	}

	@Test
	void submittingEmergencyProseRoutesDirectlyToStaffHandling() {
		String prose = "Emergency! Leo is bleeding heavily and having seizures!";
		SchedulingRequest request = this.requestService.submitRequest(this.ownerId, this.petId, prose, true);

		assertThat(request).isNotNull();
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
	}

	@Test
	void submittingRequestForUnownedPetFails() {
		assertThatThrownBy(
				() -> this.requestService.submitRequest(this.ownerId, this.otherOwnerPetId, "Checkup please", true))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does not belong to owner");
	}

	@Test
	void submittingDuplicateActiveRequestForSamePetReopensExistingRequest() {
		SchedulingRequest first = this.requestService.submitRequest(this.ownerId, this.petId, "First request", true);
		int revisionCount = this.textRevisionRepository.findAll().size();
		int jobCount = this.backgroundJobRepository.findAll().size();

		SchedulingRequest reopened = this.requestService.submitRequest(this.ownerId, this.petId, "Second request",
				true);

		assertThat(reopened.getId()).isEqualTo(first.getId());
		assertThat(this.textRevisionRepository.findAll()).hasSize(revisionCount);
		assertThat(this.backgroundJobRepository.findAll()).hasSize(jobCount);
	}

	@Test
	void getOwnerRequestProjectionAndStatusReflectsCurrentState() {
		SchedulingRequest request = this.requestService.submitRequest(this.ownerId, this.petId, "Checkup needed", true);

		Optional<OwnerRequestProjection> projection = this.requestService.getOwnerRequestProjection(request.getId(),
				this.ownerId);
		assertThat(projection).isPresent();
		assertThat(projection.get().requestId()).isEqualTo(request.getId());
		assertThat(projection.get().petName()).isEqualTo("Leo");
		assertThat(projection.get().rawState()).isEqualTo(RequestState.AWAITING_INTERPRETATION);

		// Foreign owner projection returns empty
		Optional<OwnerRequestProjection> foreignProjection = this.requestService
			.getOwnerRequestProjection(request.getId(), 2);
		assertThat(foreignProjection).isEmpty();

		Optional<RequestStatusResponse> status = this.requestService.getOwnerRequestStatus(request.getId(),
				this.ownerId);
		assertThat(status).isPresent();
		assertThat(status.get().requestId()).isEqualTo(request.getId());
		assertThat(status.get().displayState()).isEqualTo("INTERPRETING_REQUEST");
		assertThat(status.get().terminal()).isFalse();

		// Transition to AWAITING_REVIEW
		request.setState(RequestState.AWAITING_REVIEW);
		this.requestRepository.saveAndFlush(request);
		RequestStatusResponse reviewStatus = this.requestService.getOwnerRequestStatus(request.getId(), this.ownerId)
			.orElseThrow();
		assertThat(reviewStatus.displayState()).isEqualTo("REVIEW_INTERPRETATION");
		assertThat(reviewStatus.canonicalUrl()).isEqualTo("/owner/requests/" + request.getId() + "/interpretation");
		assertThat(reviewStatus.primaryAction()).isNotNull();
		assertThat(reviewStatus.primaryAction().url())
			.isEqualTo("/owner/requests/" + request.getId() + "/interpretation");

		// Transition to CLOSED
		request.setState(RequestState.CLOSED);
		this.requestRepository.saveAndFlush(request);
		RequestStatusResponse closedStatus = this.requestService.getOwnerRequestStatus(request.getId(), this.ownerId)
			.orElseThrow();
		assertThat(closedStatus.displayState()).isEqualTo("CLOSED");
		assertThat(closedStatus.terminal()).isTrue();
	}

}
