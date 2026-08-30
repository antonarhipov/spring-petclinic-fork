package org.springframework.samples.petclinic.scheduling.request;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.config.SchedulingProperties;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretationV1;
import org.springframework.samples.petclinic.scheduling.interpretation.ClinicVocabulary;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyAuditMapper;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyScreeningService;
import org.springframework.samples.petclinic.scheduling.interpretation.UrgentRequestService;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionDispatcher;
import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestWorkflowService {

	private final SchedulingRequestRepository requests;

	private final ActivePetRequestRepository activePets;

	private final RequestTextRevisionRepository texts;

	private final ConsentRecordRepository consents;

	private final InterpretationRecordRepository interpretations;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	private final OwnerRepository owners;

	private final AvailabilityRepository policies;

	private final IntegrationExecutionRepository executions;

	private final IntegrationExecutionDispatcher dispatcher;

	private final FallbackRoutingService fallback;

	private final AuditService audit;

	private final SchedulingProperties properties;

	private final Clock clock;

	private final EmergencyScreeningService emergencyScreening;

	private final UrgentRequestService urgentRequests;

	private final EmergencyAuditMapper emergencyAudit;

	public RequestWorkflowService(SchedulingRequestRepository requests, ActivePetRequestRepository activePets,
			RequestTextRevisionRepository texts, ConsentRecordRepository consents,
			InterpretationRecordRepository interpretations, RequestRevisionRepository revisions,
			RequestWindowRepository windows, OwnerRepository owners, AvailabilityRepository policies,
			IntegrationExecutionRepository executions, IntegrationExecutionDispatcher dispatcher,
			FallbackRoutingService fallback, AuditService audit, SchedulingProperties properties, Clock clock,
			EmergencyScreeningService emergencyScreening, UrgentRequestService urgentRequests,
			EmergencyAuditMapper emergencyAudit) {
		this.requests = requests;
		this.activePets = activePets;
		this.texts = texts;
		this.consents = consents;
		this.interpretations = interpretations;
		this.revisions = revisions;
		this.windows = windows;
		this.owners = owners;
		this.policies = policies;
		this.executions = executions;
		this.dispatcher = dispatcher;
		this.fallback = fallback;
		this.audit = audit;
		this.properties = properties;
		this.clock = clock;
		this.emergencyScreening = emergencyScreening;
		this.urgentRequests = urgentRequests;
		this.emergencyAudit = emergencyAudit;
	}

	@Transactional
	public SchedulingRequest createRequest(Integer ownerId, Integer petId, String sourceText) {
		String text = sourceText == null ? "" : sourceText.trim();
		if (text.length() < 10 || text.length() > 2000) {
			throw new IllegalArgumentException("SOURCE_TEXT_LENGTH");
		}
		Owner owner = this.owners.findById(ownerId).orElseThrow(OwnerResourceNotFoundException::new);
		if (owner.getPet(petId) == null) {
			throw new OwnerResourceNotFoundException();
		}
		if (this.activePets.existsById(petId)) {
			throw new IllegalStateException("ACTIVE_REQUEST_EXISTS");
		}
		Instant now = Instant.now(this.clock);
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(ownerId);
		request.setPetId(petId);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setCreatedAt(now);
		request.setUpdatedAt(now);
		request = this.requests.save(request);
		RequestTextRevision revision = newText(request.getId(), 1, text, policy.getZoneId(), now);
		EmergencyScreeningService.ScreenResult screen = this.emergencyScreening.screen(text);
		revision.setEmergencyScreenVersion(screen.version());
		revision.setEmergencyMatchedTermsJson(screen.matchedTerms().toString());
		revision = this.texts.save(revision);
		request.setActiveTextRevisionId(revision.getId());
		this.emergencyAudit.record(request.getId(), screen);
		if (screen.matched()) {
			this.urgentRequests.route(request, now);
			ActivePetRequest guard = new ActivePetRequest();
			guard.setPetId(petId);
			guard.setRequestId(request.getId());
			this.activePets.save(guard);
			return request;
		}
		ActivePetRequest guard = new ActivePetRequest();
		guard.setPetId(petId);
		guard.setRequestId(request.getId());
		this.activePets.save(guard);
		this.audit.record("OWNER", null, "REQUEST_CREATED", "SchedulingRequest", String.valueOf(request.getId()),
				request.getId(), null, "{\"petId\":" + petId + "}");
		return request;
	}

	@Transactional
	public UUID agreeToInterpret(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		SchedulingRequest request = requireOwned(requestId, ownerId);
		assertVersion(request, expectedVersion);
		if (request.getState() != RequestState.AWAITING_CONSENT) {
			throw new IllegalStateException("INVALID_STATE");
		}
		Instant now = Instant.now(this.clock);
		ConsentRecord consent = new ConsentRecord();
		consent.setTextRevisionId(request.getActiveTextRevisionId());
		consent.setDecision("AGREE");
		consent.setActorAccountId(accountId);
		consent.setDataUseCopyVersion(this.policies.currentPolicy().getConsentCopyVersion());
		consent.setDecidedAt(now);
		this.consents.save(consent);
		RequestTextRevision text = this.texts.findById(request.getActiveTextRevisionId()).orElseThrow();
		EmergencyScreeningService.ScreenResult screen = this.emergencyScreening.screen(text.getSourceText());
		this.emergencyAudit.record(request.getId(), screen);
		if (screen.matched()) {
			this.urgentRequests.route(request, now);
			return UUID.randomUUID();
		}
		String triggerKey = request.getId() + ":" + request.getActiveTextRevisionId() + ":LLM_INTERPRETATION";
		IntegrationExecution existing = this.executions.findByTriggerKey(triggerKey).orElse(null);
		if (existing != null) {
			return existing.getId();
		}
		IntegrationExecution execution = new IntegrationExecution();
		execution.setId(UUID.randomUUID());
		execution.setKind("LLM_INTERPRETATION");
		execution.setRequestId(request.getId());
		execution.setTextRevisionId(request.getActiveTextRevisionId());
		execution.setTriggerKey(triggerKey);
		execution.setState("PENDING");
		execution.setTriggeredAt(now);
		execution.setDeadlineAt(now.plus(this.properties.getLlmDeadline()));
		execution.setSchemaVersion("1.0");
		execution.setInputJson("{\"textRevisionId\":" + request.getActiveTextRevisionId() + "}");
		this.executions.save(execution);
		request.setState(RequestState.INTERPRETING);
		request.setOwnerStatusCode("INTERPRETING");
		request.setUpdatedAt(now);
		this.dispatcher.dispatchAfterCommit(execution.getId());
		this.audit.record("OWNER", accountId, "CONSENT_AGREE", "SchedulingRequest", String.valueOf(request.getId()),
				request.getId(), null, "{\"executionId\":\"" + execution.getId() + "\"}");
		return execution.getId();
	}

	@Transactional
	public void declineInterpretation(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		SchedulingRequest request = requireOwned(requestId, ownerId);
		assertVersion(request, expectedVersion);
		if (request.getState() != RequestState.AWAITING_CONSENT) {
			throw new IllegalStateException("INVALID_STATE");
		}
		Instant now = Instant.now(this.clock);
		ConsentRecord consent = new ConsentRecord();
		consent.setTextRevisionId(request.getActiveTextRevisionId());
		consent.setDecision("DECLINE");
		consent.setActorAccountId(accountId);
		consent.setDataUseCopyVersion(this.policies.currentPolicy().getConsentCopyVersion());
		consent.setDecidedAt(now);
		this.consents.save(consent);
		routeToStaff(request, "CONSENT_DECLINED", now);
		this.audit.record("OWNER", accountId, "CONSENT_DECLINE", "SchedulingRequest", String.valueOf(request.getId()),
				request.getId(), null, "{\"llm\":false}");
	}

	@Transactional
	public RequestRevision saveOwnerEdits(Long requestId, Integer ownerId, Integer expectedVersion, String visitReason,
			Integer durationMinutes) {
		SchedulingRequest request = requireOwned(requestId, ownerId);
		assertVersion(request, expectedVersion);
		if (request.getState() != RequestState.INTERPRETATION_REVIEW) {
			throw new IllegalStateException("INVALID_STATE");
		}
		RequestRevision current = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		if (!isEditable("visitReason") || !isEditable("durationMinutes")) {
			throw new IllegalStateException("FIELD_NOT_EDITABLE");
		}
		Instant now = Instant.now(this.clock);
		RequestRevision draft = copyRevision(current, this.revisions.countByRequestId(requestId) + 1, now);
		if (visitReason != null && !visitReason.isBlank()) {
			draft.setVisitReason(visitReason.trim());
		}
		if (durationMinutes != null) {
			draft.setDurationMinutes(durationMinutes);
		}
		draft.setStatus("DRAFT");
		draft = this.revisions.save(draft);
		copyWindows(current.getId(), draft.getId());
		request.setActiveRequestRevisionId(draft.getId());
		request.setUpdatedAt(now);
		return draft;
	}

	@Transactional
	public void confirm(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		SchedulingRequest request = requireOwned(requestId, ownerId);
		assertVersion(request, expectedVersion);
		if (request.getState() != RequestState.INTERPRETATION_REVIEW) {
			throw new IllegalStateException("INVALID_STATE");
		}
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		if (!isConfirmable(revision)) {
			return;
		}
		Instant now = Instant.now(this.clock);
		revision.setStatus("CONFIRMED");
		revision.setConfirmedByAccountId(accountId);
		revision.setConfirmedAt(now);
		request.setState(RequestState.READY_FOR_SUGGESTION);
		request.setOwnerStatusCode("READY_FOR_SUGGESTION");
		request.setUpdatedAt(now);
	}

	@Transactional
	public void reviseSourceText(Long requestId, Integer ownerId, Integer expectedVersion, String sourceText) {
		SchedulingRequest request = requireOwned(requestId, ownerId);
		assertVersion(request, expectedVersion);
		if (request.getState() == RequestState.CONFIRMED || request.getState() == RequestState.CLOSED) {
			throw new IllegalStateException("INVALID_STATE");
		}
		String text = sourceText == null ? "" : sourceText.trim();
		if (text.length() < 10 || text.length() > 2000) {
			throw new IllegalArgumentException("SOURCE_TEXT_LENGTH");
		}
		Instant now = Instant.now(this.clock);
		int seq = this.texts.countByRequestId(requestId) + 1;
		RequestTextRevision revision = newText(requestId, seq, text, this.policies.currentPolicy().getZoneId(), now);
		revision = this.texts.save(revision);
		request.setActiveTextRevisionId(revision.getId());
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setUpdatedAt(now);
	}

	@Transactional
	public void applyInterpretation(Long requestId, Long textRevisionId,
			org.springframework.samples.petclinic.scheduling.interpretation.InterpretationValidationResult validation,
			ClinicVocabulary vocabulary) {
		SchedulingRequest request = this.requests.findById(requestId).orElseThrow();
		if (!textRevisionId.equals(request.getActiveTextRevisionId())
				|| request.getState() != RequestState.INTERPRETING) {
			return;
		}
		Instant now = Instant.now(this.clock);
		InterpretationRecord record = new InterpretationRecord();
		record.setTextRevisionId(textRevisionId);
		record.setOrigin("LLM");
		record.setSchemaVersion("1.0");
		record.setRecognizedOutputJson(validation.recognizedJson());
		try {
			record.setUnknownFieldsJson(
					new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(validation.unknownFields()));
			record.setValidationIssuesJson(
					new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(validation.issueCodes()));
		}
		catch (Exception ex) {
			record.setUnknownFieldsJson("{}");
		}
		record.setOutcome(validation.classification().name());
		record.setCreatedAt(now);
		record = this.interpretations.save(record);
		if (validation.reviewable()) {
			RequestRevision revision = fromInterpretation(request, record, validation.recognized(), vocabulary, now);
			revision = this.revisions.save(revision);
			persistWindows(revision.getId(), validation.recognized());
			request.setActiveRequestRevisionId(revision.getId());
			boolean llmUrgent = validation.recognized().urgency() != null
					&& validation.recognized().urgency().toUpperCase().contains("EMERGENCY");
			request.setSuspectedEmergency(
					this.emergencyScreening.raiseOnly(request.isSuspectedEmergency(), false, llmUrgent));
			request.setState(RequestState.INTERPRETATION_REVIEW);
			request.setOwnerStatusCode("INTERPRETATION_REVIEW");
		}
		else {
			routeToStaff(request, validation.classification().name(), now);
		}
		request.setUpdatedAt(now);
	}

	public boolean isEditable(String field) {
		return switch (field) {
			case "visitReason", "durationMinutes", "preferredVeterinarianId" -> true;
			case "urgency", "petId", "ownerId" -> false;
			default -> false;
		};
	}

	public SchedulingRequest requireOwned(Long requestId, Integer ownerId) {
		return this.requests.findByIdAndOwnerId(requestId, ownerId).orElseThrow(OwnerResourceNotFoundException::new);
	}

	private boolean isConfirmable(RequestRevision revision) {
		return revision.getVisitReason() != null && !revision.getVisitReason().isBlank()
				&& revision.getDurationMinutes() > 0 && !"UNRESOLVED".equals(revision.getCareType())
				&& this.windows.findByRequestRevisionId(revision.getId())
					.stream()
					.anyMatch(w -> "ALLOWED".equals(w.getKind()));
	}

	private void routeToStaff(SchedulingRequest request, String reason, Instant now) {
		request.setState(RequestState.STAFF_HANDLING);
		request.setOwnerStatusCode("STAFF_HANDLING");
		request.setUpdatedAt(now);
		this.fallback.ensureQueueItem(request.getId(), reason, request.isSuspectedEmergency());
	}

	private RequestTextRevision newText(Long requestId, int sequence, String text, String zone, Instant now) {
		RequestTextRevision revision = new RequestTextRevision();
		revision.setRequestId(requestId);
		revision.setSequence(sequence);
		revision.setSourceText(text);
		revision.setSourceHash(sha256(text));
		revision.setSubmittedAt(now);
		revision.setClinicZoneId(zone);
		revision.setEmergencyScreenVersion("none");
		revision.setEmergencyMatchedTermsJson("[]");
		return revision;
	}

	private RequestRevision copyRevision(RequestRevision current, int sequence, Instant now) {
		RequestRevision draft = new RequestRevision();
		draft.setRequestId(current.getRequestId());
		draft.setSequence(sequence);
		draft.setInterpretationId(current.getInterpretationId());
		draft.setVisitReason(current.getVisitReason());
		draft.setDurationMinutes(current.getDurationMinutes());
		draft.setCareType(current.getCareType());
		draft.setSpecialtyId(current.getSpecialtyId());
		draft.setUrgency(current.getUrgency());
		draft.setPreferredVeterinarianId(current.getPreferredVeterinarianId());
		draft.setVeterinarianPreferenceStrength(current.getVeterinarianPreferenceStrength());
		draft.setClinicPolicyVersion(current.getClinicPolicyVersion());
		draft.setClinicZoneId(current.getClinicZoneId());
		draft.setCreatedAt(now);
		return draft;
	}

	private void copyWindows(Long fromId, Long toId) {
		for (RequestWindow window : this.windows.findByRequestRevisionId(fromId)) {
			RequestWindow copy = new RequestWindow();
			copy.setRequestRevisionId(toId);
			copy.setKind(window.getKind());
			copy.setStartAt(window.getStartAt());
			copy.setEndAt(window.getEndAt());
			copy.setSourcePhrase(window.getSourcePhrase());
			copy.setFallbackAllowed(window.isFallbackAllowed());
			this.windows.save(copy);
		}
	}

	private RequestRevision fromInterpretation(SchedulingRequest request, InterpretationRecord record,
			AppointmentInterpretationV1 dto, ClinicVocabulary vocabulary, Instant now) {
		RequestRevision revision = new RequestRevision();
		revision.setRequestId(request.getId());
		revision.setSequence(this.revisions.countByRequestId(request.getId()) + 1);
		revision.setInterpretationId(record.getId());
		revision.setStatus("DRAFT");
		revision.setVisitReason(dto.visitReason());
		revision.setDurationMinutes(dto.durationMinutes());
		revision.setCareType(dto.careType());
		if (dto.requiredSpecialtyCode() != null) {
			revision.setSpecialtyId(vocabulary.specialtyIdsByCode().get(dto.requiredSpecialtyCode()));
		}
		revision.setUrgency(dto.urgency());
		if (dto.preferredVeterinarianCode() != null) {
			revision
				.setPreferredVeterinarianId(vocabulary.veterinarianIdsByCode().get(dto.preferredVeterinarianCode()));
		}
		revision.setVeterinarianPreferenceStrength(dto.veterinarianPreferenceStrength());
		revision.setClinicPolicyVersion(this.policies.currentPolicy().getConfigurationVersion());
		revision.setClinicZoneId(vocabulary.clinicZoneId());
		revision.setCreatedAt(now);
		return revision;
	}

	private void persistWindows(Long revisionId, AppointmentInterpretationV1 dto) {
		saveWindows(revisionId, "ALLOWED", dto.allowedWindows());
		saveWindows(revisionId, "PREFERRED", dto.preferredWindows());
		saveWindows(revisionId, "EXCLUDED", dto.excludedWindows());
	}

	private void saveWindows(Long revisionId, String kind,
			java.util.List<AppointmentInterpretationV1.WindowV1> source) {
		for (AppointmentInterpretationV1.WindowV1 window : source) {
			if (!"RESOLVED".equals(window.resolution()) || window.resolvedStart() == null) {
				continue;
			}
			RequestWindow entity = new RequestWindow();
			entity.setRequestRevisionId(revisionId);
			entity.setKind(kind);
			entity.setStartAt(OffsetDateTime.parse(window.resolvedStart()).toInstant());
			entity.setEndAt(OffsetDateTime.parse(window.resolvedEnd()).toInstant());
			entity.setSourcePhrase(window.sourcePhrase());
			entity.setFallbackAllowed(Boolean.TRUE.equals(window.fallbackAllowed()));
			this.windows.save(entity);
		}
	}

	private void assertVersion(SchedulingRequest request, Integer expectedVersion) {
		if (expectedVersion != null && request.getVersion() != null && !expectedVersion.equals(request.getVersion())) {
			throw new StaleStateException("stale", request.getState().name(), null);
		}
	}

	private static String sha256(String text) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
