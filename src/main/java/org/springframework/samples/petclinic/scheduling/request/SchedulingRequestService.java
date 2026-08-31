package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyKeywordScreen;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingRequestService {

	private static final Logger log = LoggerFactory.getLogger(SchedulingRequestService.class);

	private final SchedulingRequestRepository requestRepository;

	private final ActiveSchedulingRequestRepository activeRequestRepository;

	private final TextRevisionRepository textRevisionRepository;

	private final BackgroundJobRepository jobRepository;

	private final OfferRepository offerRepository;

	private final OwnerRepository ownerRepository;

	private final ProtectedPayloadService payloadService;

	private final EmergencyKeywordScreen emergencyScreen;

	private final StaffFallbackPort staffFallbackPort;

	private final OwnerHistoryService ownerHistoryService;

	private final Clock clock;

	public SchedulingRequestService(SchedulingRequestRepository requestRepository,
			ActiveSchedulingRequestRepository activeRequestRepository, TextRevisionRepository textRevisionRepository,
			BackgroundJobRepository jobRepository, OfferRepository offerRepository, OwnerRepository ownerRepository,
			ProtectedPayloadService payloadService, EmergencyKeywordScreen emergencyScreen,
			StaffFallbackPort staffFallbackPort, OwnerHistoryService ownerHistoryService, Clock clock) {
		this.requestRepository = requestRepository;
		this.activeRequestRepository = activeRequestRepository;
		this.textRevisionRepository = textRevisionRepository;
		this.jobRepository = jobRepository;
		this.offerRepository = offerRepository;
		this.ownerRepository = ownerRepository;
		this.payloadService = payloadService;
		this.emergencyScreen = emergencyScreen;
		this.staffFallbackPort = staffFallbackPort;
		this.ownerHistoryService = ownerHistoryService;
		this.clock = clock;
	}

	@Transactional
	public SchedulingRequest submitRequest(Integer ownerId, Integer petId, String prose, boolean aiConsent) {
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));

		Pet pet = owner.getPets()
			.stream()
			.filter(p -> p.getId().equals(petId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Pet " + petId + " does not belong to owner " + ownerId));

		if (prose == null || prose.isBlank()) {
			throw new IllegalArgumentException("Scheduling request prose must not be blank");
		}
		if (prose.length() > 2000) {
			throw new IllegalArgumentException("Scheduling request prose must not exceed 2000 characters");
		}

		Optional<ActiveSchedulingRequest> existingActive = this.activeRequestRepository.findById(petId);
		if (existingActive.isPresent()) {
			throw new IllegalStateException("Active scheduling request already exists for pet " + petId);
		}

		Instant now = this.clock.instant();
		ProtectedPayload prosePayload = this.payloadService.encrypt(prose);
		ProtectedPayload consentPayload = this.payloadService
			.encrypt("{\"consented\":" + aiConsent + ",\"timestamp\":\"" + now + "\"}");

		EmergencyKeywordScreen.EmergencyScreenResult screenResult = this.emergencyScreen.screen(prose);

		RequestState initialState;
		if (screenResult.emergencyDetected()) {
			initialState = RequestState.STAFF_HANDLING;
		}
		else if (aiConsent) {
			initialState = RequestState.AWAITING_INTERPRETATION;
		}
		else {
			initialState = RequestState.STAFF_HANDLING;
		}

		SchedulingRequest request = new SchedulingRequest(ownerId, petId, initialState, now);
		SchedulingRequest savedRequest = this.requestRepository.save(request);

		ActiveSchedulingRequest activeRequest = new ActiveSchedulingRequest(petId, savedRequest.getId());
		this.activeRequestRepository.save(activeRequest);

		TextRevision textRevision = new TextRevision(savedRequest, 1, now, prosePayload, consentPayload);
		TextRevision savedTextRev = this.textRevisionRepository.save(textRevision);
		savedRequest.setCurrentTextRevision(savedTextRev);
		this.requestRepository.save(savedRequest);

		this.ownerHistoryService.recordOwnerHistory(ownerId, petId, savedRequest.getId(), "REQUEST_SUBMITTED",
				"Scheduling request submitted with " + (screenResult.emergencyDetected() ? "emergency routing"
						: (aiConsent ? "AI interpretation" : "manual staff review")),
				null);

		if (screenResult.emergencyDetected()) {
			this.staffFallbackPort.sendToFallbackQueue(savedRequest.getId(), "EMERGENCY_PROSE",
					Urgency.EMERGENCY_SUSPECTED,
					"Prose flagged for emergency keywords: " + String.join(", ", screenResult.matchedKeywords()));
			log.info("Request {} routed directly to staff fallback due to emergency keywords", savedRequest.getId());
		}
		else if (aiConsent) {
			BackgroundJob job = new BackgroundJob(JobType.INTERPRETATION, savedTextRev, null, JobState.PENDING, now);
			this.jobRepository.save(job);
			log.info("Created interpretation background job for request {}", savedRequest.getId());
		}
		else {
			this.staffFallbackPort.sendToFallbackQueue(savedRequest.getId(), "DECLINED_AI_CONSENT", Urgency.ROUTINE,
					"Owner declined automated AI interpretation");
			log.info("Request {} routed directly to staff fallback due to declined consent", savedRequest.getId());
		}

		return savedRequest;
	}

	@Transactional(readOnly = true)
	public List<OwnerRequestProjection> getOwnerRequests(Integer ownerId) {
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));

		List<SchedulingRequest> requests = this.requestRepository.findByOwnerIdOrderBySubmittedAtDesc(ownerId);
		return requests.stream().map(req -> {
			String petName = owner.getPets()
				.stream()
				.filter(p -> p.getId().equals(req.getPetId()))
				.map(Pet::getName)
				.findFirst()
				.orElse("Pet");

			Optional<Offer> activeOffer = this.offerRepository.findByRequestIdAndState(req.getId(), OfferState.HELD);
			Long offerId = activeOffer.map(Offer::getId).orElse(null);
			Instant expiresAt = activeOffer.map(Offer::getExpiresAt).orElse(null);

			return OwnerRequestProjection.from(req, petName, offerId, expiresAt);
		}).toList();
	}

	@Transactional(readOnly = true)
	public Optional<OwnerRequestProjection> getOwnerRequestProjection(Long requestId, Integer ownerId) {
		return this.requestRepository.findByIdAndOwnerId(requestId, ownerId).map(req -> {
			Owner owner = this.ownerRepository.findById(ownerId).orElse(null);
			String petName = "Pet";
			if (owner != null) {
				petName = owner.getPets()
					.stream()
					.filter(p -> p.getId().equals(req.getPetId()))
					.map(Pet::getName)
					.findFirst()
					.orElse("Pet");
			}

			Optional<Offer> activeOffer = this.offerRepository.findByRequestIdAndState(req.getId(), OfferState.HELD);
			Long offerId = activeOffer.map(Offer::getId).orElse(null);
			Instant expiresAt = activeOffer.map(Offer::getExpiresAt).orElse(null);

			return OwnerRequestProjection.from(req, petName, offerId, expiresAt);
		});
	}

	@Transactional(readOnly = true)
	public Optional<SchedulingRequest> getRequest(Long requestId, Integer ownerId) {
		return this.requestRepository.findByIdAndOwnerId(requestId, ownerId);
	}

	@Transactional(readOnly = true)
	public Optional<RequestStatusResponse> getOwnerRequestStatus(Long requestId, Integer ownerId) {
		return this.requestRepository.findByIdAndOwnerId(requestId, ownerId).map(req -> {
			Optional<Offer> activeOffer = this.offerRepository.findByRequestIdAndState(req.getId(), OfferState.HELD);
			Long offerId = activeOffer.map(Offer::getId).orElse(null);
			Instant offerExpiresAt = activeOffer.map(Offer::getExpiresAt).orElse(null);

			Instant now = this.clock.instant();
			Instant idleExpiresAt = now.plus(30, ChronoUnit.MINUTES);
			Instant warningAt = now.plus(25, ChronoUnit.MINUTES);

			int version = (req.getCurrentWorkflowRevision() != null) ? req.getCurrentWorkflowRevision().getVersion()
					: (req.getCurrentTextRevision() != null ? req.getCurrentTextRevision().getVersion() : 1);

			String displayState;
			String canonicalUrl = "/owner/requests/" + req.getId();
			RequestStatusResponse.PrimaryAction primaryAction = null;
			boolean terminal = false;
			int pollAfterMillis = 2000;
			boolean urgentGuidance = false;

			switch (req.getState()) {
				case AWAITING_INTERPRETATION -> {
					displayState = "INTERPRETING_REQUEST";
					canonicalUrl = "/owner/requests/" + req.getId();
					pollAfterMillis = 2000;
				}
				case AWAITING_REVIEW -> {
					displayState = "REVIEW_INTERPRETATION";
					canonicalUrl = "/owner/requests/" + req.getId() + "/interpretation";
					primaryAction = new RequestStatusResponse.PrimaryAction("Review Details", canonicalUrl);
					pollAfterMillis = 5000;
				}
				case READY_TO_MATCH -> {
					displayState = "FINDING_APPOINTMENT";
					canonicalUrl = "/owner/requests/" + req.getId();
					pollAfterMillis = 2000;
				}
				case OFFERED -> {
					displayState = "APPOINTMENT_OFFERED";
					if (offerId != null) {
						canonicalUrl = "/owner/requests/" + req.getId() + "/offers/" + offerId;
						primaryAction = new RequestStatusResponse.PrimaryAction("Review Offer", canonicalUrl);
					}
					pollAfterMillis = 5000;
				}
				case STAFF_HANDLING -> {
					displayState = "WITH_CLINIC_STAFF";
					canonicalUrl = "/owner/requests/" + req.getId();
					pollAfterMillis = 5000;
				}
				case CONFIRMED -> {
					displayState = "CONFIRMED";
					if (req.getAppointmentId() != null) {
						canonicalUrl = "/owner/appointments/" + req.getAppointmentId();
						primaryAction = new RequestStatusResponse.PrimaryAction("View Appointment", canonicalUrl);
					}
					terminal = true;
					pollAfterMillis = 10000;
				}
				case CLOSED, WITHDRAWN -> {
					displayState = "CLOSED";
					canonicalUrl = "/owner/requests/" + req.getId();
					terminal = true;
					pollAfterMillis = 10000;
				}
				default -> {
					displayState = "WITH_CLINIC_STAFF";
					canonicalUrl = "/owner/requests/" + req.getId();
					pollAfterMillis = 5000;
				}
			}

			return new RequestStatusResponse(req.getId(), version, displayState, canonicalUrl, primaryAction,
					now.toString(), idleExpiresAt.toString(), warningAt.toString(),
					offerExpiresAt != null ? offerExpiresAt.toString() : null, urgentGuidance, terminal,
					pollAfterMillis);
		});
	}

}
