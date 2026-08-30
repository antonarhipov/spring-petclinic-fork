package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.scheduling.config.SchedulingProperties;
import org.springframework.samples.petclinic.scheduling.interpretation.AppointmentInterpretationV1;
import org.springframework.samples.petclinic.scheduling.interpretation.ClinicVocabulary;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyAuditMapper;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyScreeningService;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationClassification;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationValidationResult;
import org.springframework.samples.petclinic.scheduling.interpretation.UrgentRequestService;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionDispatcher;
import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestWorkflowServiceTests {

	private SchedulingRequestRepository requests;

	private ActivePetRequestRepository activePets;

	private RequestTextRevisionRepository texts;

	private ConsentRecordRepository consents;

	private InterpretationRecordRepository interpretations;

	private RequestRevisionRepository revisions;

	private RequestWindowRepository windows;

	private OwnerRepository owners;

	private AvailabilityRepository policies;

	private IntegrationExecutionRepository executions;

	private IntegrationExecutionDispatcher dispatcher;

	private FallbackRoutingService fallback;

	private RequestWorkflowService workflow;

	@BeforeEach
	void setup() {
		this.requests = mock(SchedulingRequestRepository.class);
		this.activePets = mock(ActivePetRequestRepository.class);
		this.texts = mock(RequestTextRevisionRepository.class);
		this.consents = mock(ConsentRecordRepository.class);
		this.interpretations = mock(InterpretationRecordRepository.class);
		this.revisions = mock(RequestRevisionRepository.class);
		this.windows = mock(RequestWindowRepository.class);
		this.owners = mock(OwnerRepository.class);
		this.policies = mock(AvailabilityRepository.class);
		this.executions = mock(IntegrationExecutionRepository.class);
		this.dispatcher = mock(IntegrationExecutionDispatcher.class);
		this.fallback = mock(FallbackRoutingService.class);
		AuditService audit = mock(AuditService.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		policy.setZoneId("America/Chicago");
		when(this.policies.currentPolicy()).thenReturn(policy);
		RequestTextRevision source = new RequestTextRevision();
		source.setSourceText("Need a checkup for my dog");
		when(this.texts.findById(any())).thenReturn(Optional.of(source));
		when(this.texts.save(any())).thenAnswer(inv -> inv.getArgument(0));
		EmergencyScreeningService screening = mock(EmergencyScreeningService.class);
		when(screening.screen(any()))
			.thenReturn(new EmergencyScreeningService.ScreenResult("emergency-v1", List.of(), false));
		when(screening.raiseOnly(anyBoolean(), anyBoolean(), anyBoolean())).thenAnswer(inv -> inv.getArgument(0));
		this.workflow = new RequestWorkflowService(this.requests, this.activePets, this.texts, this.consents,
				this.interpretations, this.revisions, this.windows, this.owners, this.policies, this.executions,
				this.dispatcher, this.fallback, audit, new SchedulingProperties(),
				Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), screening,
				mock(UrgentRequestService.class), mock(EmergencyAuditMapper.class));
	}

	@Test
	void createRequestRejectsUnownedPetAndDuplicateActive() {
		Owner owner = new Owner();
		owner.setId(1);
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(7);
		when(this.owners.findById(1)).thenReturn(Optional.of(owner));
		assertThatThrownBy(() -> this.workflow.createRequest(1, 99, "Need a checkup for my dog"))
			.isInstanceOf(OwnerResourceNotFoundException.class);
		when(this.activePets.existsById(7)).thenReturn(true);
		assertThatThrownBy(() -> this.workflow.createRequest(1, 7, "Need a checkup for my dog"))
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void agreeCreatesSingleLlmExecution() {
		SchedulingRequest request = awaiting();
		when(this.requests.findByIdAndOwnerId(3L, 1)).thenReturn(Optional.of(request));
		when(this.executions.findByTriggerKey(anyString())).thenReturn(Optional.empty());
		when(this.executions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		UUID id = this.workflow.agreeToInterpret(3L, 1, 10L, 0);
		assertThat(id).isNotNull();
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETING);
		verify(this.dispatcher).dispatchAfterCommit(id);
	}

	@Test
	void declineSkipsLlmAndRoutesToStaff() {
		SchedulingRequest request = awaiting();
		when(this.requests.findByIdAndOwnerId(3L, 1)).thenReturn(Optional.of(request));
		when(this.fallback.ensureQueueItem(any(), anyString(), anyBoolean())).thenReturn(new StaffQueueItem());
		this.workflow.declineInterpretation(3L, 1, 10L, 0);
		assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
		verify(this.dispatcher, never()).dispatchAfterCommit(any());
	}

	@Test
	void confirmationRequiresValidRevision() {
		SchedulingRequest request = awaiting();
		request.setState(RequestState.INTERPRETATION_REVIEW);
		request.setActiveRequestRevisionId(8L);
		when(this.requests.findByIdAndOwnerId(3L, 1)).thenReturn(Optional.of(request));
		RequestRevision revision = new RequestRevision();
		revision.setVisitReason("");
		revision.setCareType("UNRESOLVED");
		when(this.revisions.findById(8L)).thenReturn(Optional.of(revision));
		when(this.windows.findByRequestRevisionId(8L)).thenReturn(List.of());
		this.workflow.confirm(3L, 1, 10L, 0);
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETATION_REVIEW);
	}

	@Test
	void applyReviewableInterpretationPersistsRevisionAndResolvedWindows() {
		SchedulingRequest request = interpreting();
		when(this.requests.findById(3L)).thenReturn(Optional.of(request));
		when(this.revisions.countByRequestId(3L)).thenReturn(0);
		ClinicVocabulary vocabulary = vocabulary();
		when(this.interpretations.save(any())).thenAnswer(invocation -> {
			var record = invocation.<InterpretationRecord>getArgument(0);
			ReflectionTestUtils.setField(record, "id", 41L);
			return record;
		});
		when(this.revisions.save(any())).thenAnswer(invocation -> {
			var revision = invocation.<RequestRevision>getArgument(0);
			ReflectionTestUtils.setField(revision, "id", 51L);
			return revision;
		});

		this.workflow.applyInterpretation(3L, 4L,
				new InterpretationValidationResult(InterpretationClassification.VALID_REVIEWABLE, interpretationDto(),
						java.util.Map.of("confidence", 0.8), java.util.List.of(), "{\"visitReason\":\"Annual exam\"}"),
				vocabulary);

		ArgumentCaptor<RequestRevision> revisionCaptor = ArgumentCaptor.forClass(RequestRevision.class);
		verify(this.revisions).save(revisionCaptor.capture());
		RequestRevision revision = revisionCaptor.getValue();
		assertThat(revision.getRequestId()).isEqualTo(3L);
		assertThat(revision.getInterpretationId()).isEqualTo(41L);
		assertThat(revision.getVisitReason()).isEqualTo("Annual exam");
		assertThat(revision.getDurationMinutes()).isEqualTo(30);
		assertThat(revision.getSpecialtyId()).isEqualTo(12);
		assertThat(revision.getPreferredVeterinarianId()).isEqualTo(8);
		assertThat(revision.getClinicZoneId()).isEqualTo("America/Chicago");
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETATION_REVIEW);
		assertThat(request.getActiveRequestRevisionId()).isEqualTo(51L);

		ArgumentCaptor<RequestWindow> windowCaptor = ArgumentCaptor.forClass(RequestWindow.class);
		verify(this.windows, org.mockito.Mockito.times(3)).save(windowCaptor.capture());
		assertThat(windowCaptor.getAllValues()).extracting(RequestWindow::getKind)
			.containsExactly("ALLOWED", "PREFERRED", "EXCLUDED");
		assertThat(windowCaptor.getAllValues().get(0).getStartAt()).isEqualTo(Instant.parse("2026-01-05T15:00:00Z"));
		assertThat(windowCaptor.getAllValues().get(0).getEndAt()).isEqualTo(Instant.parse("2026-01-05T15:30:00Z"));
	}

	@Test
	void applyNeedsStaffAndInvalidInterpretationsRouteToStaff() {
		for (InterpretationClassification classification : List.of(InterpretationClassification.VALID_NEEDS_STAFF,
				InterpretationClassification.INVALID_STRUCTURED_OUTPUT)) {
			SchedulingRequest request = interpreting();
			when(this.requests.findById(3L)).thenReturn(Optional.of(request));
			when(this.interpretations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
			when(this.fallback.ensureQueueItem(eq(3L), eq(classification.name()), anyBoolean()))
				.thenReturn(new StaffQueueItem());

			this.workflow.applyInterpretation(3L, 4L, new InterpretationValidationResult(classification,
					interpretationDto(), java.util.Map.of(), List.of(classification.name()), "{}"), vocabulary());

			assertThat(request.getState()).isEqualTo(RequestState.STAFF_HANDLING);
			assertThat(request.getOwnerStatusCode()).isEqualTo("STAFF_HANDLING");
			verify(this.fallback).ensureQueueItem(3L, classification.name(), false);
		}
	}

	@Test
	void applyInterpretationIgnoresSupersededTextRevision() {
		SchedulingRequest request = interpreting();
		request.setActiveTextRevisionId(99L);
		when(this.requests.findById(3L)).thenReturn(Optional.of(request));

		this.workflow.applyInterpretation(3L, 4L,
				new InterpretationValidationResult(InterpretationClassification.VALID_REVIEWABLE, interpretationDto(),
						java.util.Map.of(), List.of(), "{}"),
				vocabulary());

		verify(this.interpretations, never()).save(any());
		verify(this.revisions, never()).save(any());
		verify(this.fallback, never()).ensureQueueItem(any(), anyString(), anyBoolean());
		assertThat(request.getState()).isEqualTo(RequestState.INTERPRETING);
	}

	@Test
	void ownerEditsCreateDraftAndCopyWindowsWithoutDispatchingLlm() {
		SchedulingRequest request = interpreting();
		request.setState(RequestState.INTERPRETATION_REVIEW);
		request.setActiveRequestRevisionId(8L);
		when(this.requests.findByIdAndOwnerId(3L, 1)).thenReturn(Optional.of(request));
		RequestRevision current = revision(8L);
		when(this.revisions.findById(8L)).thenReturn(Optional.of(current));
		when(this.revisions.countByRequestId(3L)).thenReturn(1);
		RequestWindow currentWindow = window(8L, "ALLOWED");
		when(this.windows.findByRequestRevisionId(8L)).thenReturn(List.of(currentWindow));
		when(this.revisions.save(any())).thenAnswer(invocation -> {
			RequestRevision draft = invocation.getArgument(0);
			ReflectionTestUtils.setField(draft, "id", 9L);
			return draft;
		});

		RequestRevision draft = this.workflow.saveOwnerEdits(3L, 1, null, "  Updated reason ", 45);

		assertThat(draft.getStatus()).isEqualTo("DRAFT");
		assertThat(draft.getVisitReason()).isEqualTo("Updated reason");
		assertThat(draft.getDurationMinutes()).isEqualTo(45);
		assertThat(request.getActiveRequestRevisionId()).isEqualTo(9L);
		ArgumentCaptor<RequestWindow> copied = ArgumentCaptor.forClass(RequestWindow.class);
		verify(this.windows).save(copied.capture());
		assertThat(copied.getValue().getRequestRevisionId()).isEqualTo(9L);
		assertThat(copied.getValue().getKind()).isEqualTo("ALLOWED");
		verify(this.dispatcher, never()).dispatchAfterCommit(any());
	}

	@Test
	void revisingSourceTextCreatesNewConsentRevisionAndDoesNotDispatchLlm() {
		SchedulingRequest request = interpreting();
		request.setState(RequestState.INTERPRETATION_REVIEW);
		when(this.requests.findByIdAndOwnerId(3L, 1)).thenReturn(Optional.of(request));
		when(this.texts.countByRequestId(3L)).thenReturn(1);
		when(this.texts.save(any())).thenAnswer(invocation -> {
			RequestTextRevision revision = invocation.getArgument(0);
			ReflectionTestUtils.setField(revision, "id", 10L);
			return revision;
		});

		this.workflow.reviseSourceText(3L, 1, null, "  My cat needs a follow-up visit  ");

		ArgumentCaptor<RequestTextRevision> textCaptor = ArgumentCaptor.forClass(RequestTextRevision.class);
		verify(this.texts).save(textCaptor.capture());
		assertThat(textCaptor.getValue().getSequence()).isEqualTo(2);
		assertThat(textCaptor.getValue().getSourceText()).isEqualTo("My cat needs a follow-up visit");
		assertThat(request.getActiveTextRevisionId()).isEqualTo(10L);
		assertThat(request.getState()).isEqualTo(RequestState.AWAITING_CONSENT);
		assertThat(request.getOwnerStatusCode()).isEqualTo("AWAITING_CONSENT");
		verify(this.dispatcher, never()).dispatchAfterCommit(any());
	}

	@Test
	void ownerEditableFieldsDoNotIncludeUrgency() {
		assertThat(this.workflow.isEditable("visitReason")).isTrue();
		assertThat(this.workflow.isEditable("urgency")).isFalse();
		assertThat(this.workflow.isEditable("petId")).isFalse();
	}

	private SchedulingRequest awaiting() {
		SchedulingRequest request = new SchedulingRequest();
		request.setOwnerId(1);
		request.setPetId(7);
		request.setState(RequestState.AWAITING_CONSENT);
		request.setOwnerStatusCode("AWAITING_CONSENT");
		request.setActiveTextRevisionId(4L);
		return request;
	}

	private SchedulingRequest interpreting() {
		SchedulingRequest request = awaiting();
		ReflectionTestUtils.setField(request, "id", 3L);
		request.setState(RequestState.INTERPRETING);
		request.setOwnerStatusCode("INTERPRETING");
		return request;
	}

	private ClinicVocabulary vocabulary() {
		return new ClinicVocabulary(java.util.Set.of(30), java.util.Set.of("general"), java.util.Set.of("smith"),
				java.util.Map.of("general", 12), java.util.Map.of("smith", 8), "America/Chicago");
	}

	private AppointmentInterpretationV1 interpretationDto() {
		AppointmentInterpretationV1.WindowV1 allowed = new AppointmentInterpretationV1.WindowV1("next Monday morning",
				"2026-01-05T09:00:00-06:00", "2026-01-05T09:30:00-06:00", "RESOLVED", true);
		AppointmentInterpretationV1.WindowV1 preferred = new AppointmentInterpretationV1.WindowV1("early morning",
				"2026-01-05T09:00:00-06:00", "2026-01-05T09:30:00-06:00", "RESOLVED", false);
		AppointmentInterpretationV1.WindowV1 excluded = new AppointmentInterpretationV1.WindowV1("during lunch",
				"2026-01-05T12:00:00-06:00", "2026-01-05T13:00:00-06:00", "RESOLVED", false);
		return new AppointmentInterpretationV1("1.0", "Annual exam", 30, "WELLNESS", "general", List.of(allowed),
				List.of(preferred), List.of(excluded), "smith", "PREFERRED", "ROUTINE", List.of(), List.of());
	}

	private RequestRevision revision(Long id) {
		RequestRevision revision = new RequestRevision();
		ReflectionTestUtils.setField(revision, "id", id);
		revision.setRequestId(3L);
		revision.setSequence(1);
		revision.setInterpretationId(41L);
		revision.setStatus("DRAFT");
		revision.setVisitReason("Original reason");
		revision.setDurationMinutes(30);
		revision.setCareType("WELLNESS");
		revision.setUrgency("ROUTINE");
		revision.setVeterinarianPreferenceStrength("PREFERRED");
		revision.setClinicZoneId("America/Chicago");
		return revision;
	}

	private RequestWindow window(Long revisionId, String kind) {
		RequestWindow window = new RequestWindow();
		window.setRequestRevisionId(revisionId);
		window.setKind(kind);
		window.setStartAt(Instant.parse("2026-01-05T09:00:00Z"));
		window.setEndAt(Instant.parse("2026-01-05T09:30:00Z"));
		window.setSourcePhrase("morning");
		window.setFallbackAllowed(true);
		return window;
	}

}
