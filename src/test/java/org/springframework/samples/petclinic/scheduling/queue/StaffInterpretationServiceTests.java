package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.audit.OwnerHistoryService;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.audit.ProtectedPayloadService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobType;
import org.springframework.samples.petclinic.scheduling.queue.StaffInterpretationService.ManualInterpretationCommand;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.TextRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffInterpretationServiceTests {

	@Mock
	private QueueItemRepository queueItemRepository;

	@Mock
	private SchedulingRequestRepository requestRepository;

	@Mock
	private WorkflowRevisionRepository workflowRevisionRepository;

	@Mock
	private BackgroundJobRepository jobRepository;

	@Mock
	private ProtectedPayloadService payloadService;

	@Mock
	private AuditService auditService;

	@Mock
	private OwnerHistoryService ownerHistoryService;

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneId.of("UTC"));

	private StaffInterpretationService interpretationService;

	private EmergencyClearanceService emergencyClearanceService;

	private SchedulingRequest request;

	private QueueItem queueItem;

	@BeforeEach
	void setUp() {
		this.interpretationService = new StaffInterpretationService(this.queueItemRepository, this.requestRepository,
				this.workflowRevisionRepository, this.jobRepository, this.payloadService, this.auditService,
				this.ownerHistoryService, this.clock);

		this.emergencyClearanceService = new EmergencyClearanceService(this.queueItemRepository, this.requestRepository,
				this.workflowRevisionRepository, this.payloadService, this.auditService, this.ownerHistoryService,
				this.clock);

		this.request = new SchedulingRequest(1, 1, RequestState.STAFF_HANDLING, this.clock.instant());
		this.request.setId(100L);
		ProtectedPayload textPayload = new ProtectedPayload();
		textPayload.setId(1L);
		TextRevision textRevision = new TextRevision(this.request, 1, this.clock.instant(), textPayload, textPayload);
		this.request.setCurrentTextRevision(textRevision);

		this.queueItem = new QueueItem(this.request, null, QueueState.IN_REVIEW, "DECLINED_AI_CONSENT", Urgency.ROUTINE,
				this.clock.instant());
		this.queueItem.setId(1L);
		this.queueItem.setAssigneeAccountId(10L);
		this.queueItem.setVersion(0L);
	}

	@Test
	void manualInterpretationWithConfirmationRequestedSetsAwaitingOwner() {
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		ProtectedPayload dummyReason = new ProtectedPayload();
		dummyReason.setId(50L);
		when(this.payloadService.store(any(), eq("VISIT_REASON"), eq(1), eq("text/plain"), eq("Vaccine check")))
			.thenReturn(dummyReason);

		when(this.workflowRevisionRepository.save(any(WorkflowRevision.class))).thenAnswer(inv -> {
			WorkflowRevision wr = inv.getArgument(0);
			wr.setId(201L);
			return wr;
		});

		ManualInterpretationCommand cmd = new ManualInterpretationCommand(1L, 10L, "Vaccine check", 30, 2, null,
				Urgency.ROUTINE, List.of(), true, null, null, 0L);

		WorkflowRevision result = this.interpretationService.recordManualInterpretation(cmd);

		assertThat(result.getState()).isEqualTo(WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED);
		assertThat(this.request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);
		assertThat(this.queueItem.getState()).isEqualTo(QueueState.AWAITING_OWNER);
		assertThat(this.queueItem.getAwaitingReason()).isEqualTo(AwaitingReason.INTERPRETATION_CONFIRMATION);
		verify(this.auditService).recordEvent(eq(10L), eq("STAFF_MANUAL_INTERPRETATION"), eq("QueueItem"), eq("1"),
				eq("SUCCESS"), any(), any(), eq(50L));
	}

	@Test
	void manualInterpretationCannotBypassOwnerConfirmation() {
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		ProtectedPayload dummyReason = new ProtectedPayload();
		dummyReason.setId(50L);
		when(this.payloadService.store(any(), eq("VISIT_REASON"), eq(1), eq("text/plain"), eq("Dental cleaning")))
			.thenReturn(dummyReason);

		when(this.workflowRevisionRepository.save(any(WorkflowRevision.class))).thenAnswer(inv -> {
			WorkflowRevision wr = inv.getArgument(0);
			wr.setId(202L);
			return wr;
		});

		ManualInterpretationCommand cmd = new ManualInterpretationCommand(1L, 10L, "Dental cleaning", 30, 1, null,
				Urgency.ROUTINE, List.of(), false, null, null, 0L);

		WorkflowRevision result = this.interpretationService.recordManualInterpretation(cmd);

		assertThat(result.getState()).isEqualTo(WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED);
		assertThat(this.request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);
		assertThat(this.queueItem.getState()).isEqualTo(QueueState.AWAITING_OWNER);
	}

	@Test
	void emergencyClearanceDowngradesUrgencyAndRequestsOwnerReconfirmation() {
		this.queueItem.setUrgency(Urgency.EMERGENCY_SUSPECTED);
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));

		ProtectedPayload justPayload = new ProtectedPayload();
		justPayload.setId(60L);
		when(this.payloadService.store(any(), eq("EMERGENCY_CLEARANCE_JUSTIFICATION"), eq(1), eq("text/plain"),
				eq("Owner called, bleeding stopped completely")))
			.thenReturn(justPayload);

		when(this.workflowRevisionRepository.save(any(WorkflowRevision.class))).thenAnswer(inv -> {
			WorkflowRevision wr = inv.getArgument(0);
			wr.setId(203L);
			return wr;
		});

		WorkflowRevision result = this.emergencyClearanceService.clearEmergency(1L, 10L, Urgency.ROUTINE,
				"Owner called, bleeding stopped completely", null, null, 0L);

		assertThat(result.getUrgency()).isEqualTo(Urgency.ROUTINE);
		assertThat(result.getState()).isEqualTo(WorkflowRevisionState.OWNER_CONFIRMATION_REQUIRED);
		assertThat(this.request.getState()).isEqualTo(RequestState.AWAITING_REVIEW);
		assertThat(this.queueItem.getUrgency()).isEqualTo(Urgency.ROUTINE);
		assertThat(this.queueItem.getState()).isEqualTo(QueueState.AWAITING_OWNER);
		assertThat(this.queueItem.getAwaitingReason()).isEqualTo(AwaitingReason.INTERPRETATION_CONFIRMATION);

		verify(this.auditService).recordEvent(eq(10L), eq("EMERGENCY_CLEARED"), eq("QueueItem"), eq("1"), eq("SUCCESS"),
				any(), any(), eq(60L));
	}

}
