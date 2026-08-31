package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueAssignmentServiceTests {

	@Mock
	private QueueItemRepository queueItemRepository;

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private AuditService auditService;

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneId.of("UTC"));

	private QueueAssignmentService assignmentService;

	private Account staff1;

	private Account staff2;

	private Account ownerAccount;

	private SchedulingRequest request;

	private QueueItem queueItem;

	@BeforeEach
	void setUp() {
		this.assignmentService = new QueueAssignmentService(this.queueItemRepository, this.accountRepository,
				this.auditService, this.clock);

		this.staff1 = new Account();
		this.staff1.setId(10L);
		this.staff1.setUsername("staff1");
		this.staff1.setRole(Role.STAFF);

		this.staff2 = new Account();
		this.staff2.setId(11L);
		this.staff2.setUsername("staff2");
		this.staff2.setRole(Role.STAFF);

		this.ownerAccount = new Account();
		this.ownerAccount.setId(20L);
		this.ownerAccount.setUsername("owner1");
		this.ownerAccount.setRole(Role.OWNER);

		this.request = new SchedulingRequest(1, 1, RequestState.STAFF_HANDLING, this.clock.instant());
		this.request.setId(100L);

		this.queueItem = new QueueItem(this.request, null, QueueState.NEW, "DECLINED_AI_CONSENT", Urgency.ROUTINE,
				this.clock.instant());
		this.queueItem.setVersion(0L);
	}

	@Test
	void claimNewQueueItemTransitionsToInReview() {
		when(this.accountRepository.findById(10L)).thenReturn(Optional.of(this.staff1));
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		when(this.queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

		QueueItem result = this.assignmentService.claim(1L, 10L);

		assertThat(result.getState()).isEqualTo(QueueState.IN_REVIEW);
		assertThat(result.getAssigneeAccountId()).isEqualTo(10L);
		verify(this.auditService).recordEvent(eq(10L), eq("QUEUE_ITEM_CLAIMED"), eq("QueueItem"), eq("1"),
				eq("SUCCESS"), any(), any(), any());
	}

	@Test
	void claimAwaitingOwnerPreservesState() {
		this.queueItem.setState(QueueState.AWAITING_OWNER);
		this.queueItem.setAwaitingReason(AwaitingReason.CONTACT_REQUIRED);

		when(this.accountRepository.findById(10L)).thenReturn(Optional.of(this.staff1));
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		when(this.queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

		QueueItem result = this.assignmentService.claim(1L, 10L);

		assertThat(result.getState()).isEqualTo(QueueState.AWAITING_OWNER);
		assertThat(result.getAwaitingReason()).isEqualTo(AwaitingReason.CONTACT_REQUIRED);
		assertThat(result.getAssigneeAccountId()).isEqualTo(10L);
	}

	@Test
	void unclaimInReviewTransitionsBackToNew() {
		this.queueItem.setState(QueueState.IN_REVIEW);
		this.queueItem.setAssigneeAccountId(10L);

		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		when(this.queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

		QueueItem result = this.assignmentService.unclaim(1L, 10L);

		assertThat(result.getState()).isEqualTo(QueueState.NEW);
		assertThat(result.getAssigneeAccountId()).isNull();
		verify(this.auditService).recordEvent(eq(10L), eq("QUEUE_ITEM_UNCLAIMED"), eq("QueueItem"), eq("1"),
				eq("SUCCESS"), any(), any(), any());
	}

	@Test
	void reassignUpdatesAssignee() {
		this.queueItem.setState(QueueState.IN_REVIEW);
		this.queueItem.setAssigneeAccountId(10L);

		when(this.accountRepository.findById(11L)).thenReturn(Optional.of(this.staff2));
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));
		when(this.queueItemRepository.save(any(QueueItem.class))).thenAnswer(inv -> inv.getArgument(0));

		QueueItem result = this.assignmentService.reassign(1L, 11L, 10L);

		assertThat(result.getState()).isEqualTo(QueueState.IN_REVIEW);
		assertThat(result.getAssigneeAccountId()).isEqualTo(11L);
		verify(this.auditService).recordEvent(eq(10L), eq("QUEUE_ITEM_REASSIGNED"), eq("QueueItem"), eq("1"),
				eq("SUCCESS"), any(), any(), any());
	}

	@Test
	void claimByNonStaffThrowsException() {
		when(this.accountRepository.findById(20L)).thenReturn(Optional.of(this.ownerAccount));

		assertThatThrownBy(() -> this.assignmentService.claim(1L, 20L)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not a staff member");
	}

	@Test
	void claimResolvedQueueItemThrowsException() {
		this.queueItem.setState(QueueState.RESOLVED);

		when(this.accountRepository.findById(10L)).thenReturn(Optional.of(this.staff1));
		when(this.queueItemRepository.findById(1L)).thenReturn(Optional.of(this.queueItem));

		assertThatThrownBy(() -> this.assignmentService.claim(1L, 10L)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Cannot claim inactive queue item");
	}

}
