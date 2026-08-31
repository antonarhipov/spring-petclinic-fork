package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QueueAssignmentService {

	private static final Logger log = LoggerFactory.getLogger(QueueAssignmentService.class);

	private final QueueItemRepository queueItemRepository;

	private final AccountRepository accountRepository;

	private final AuditService auditService;

	private final Clock clock;

	public QueueAssignmentService(QueueItemRepository queueItemRepository, AccountRepository accountRepository,
			AuditService auditService, Clock clock) {
		this.queueItemRepository = queueItemRepository;
		this.accountRepository = accountRepository;
		this.auditService = auditService;
		this.clock = clock;
	}

	public QueueItem claim(Long queueItemId, Long staffAccountId) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");
		Objects.requireNonNull(staffAccountId, "staffAccountId must not be null");

		Account staff = this.accountRepository.findById(staffAccountId)
			.orElseThrow(() -> new IllegalArgumentException("Staff account not found: " + staffAccountId));
		if (staff.getRole() != Role.STAFF) {
			throw new IllegalArgumentException("Account is not a staff member: " + staffAccountId);
		}

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot claim inactive queue item in state: " + queueItem.getState());
		}

		Instant now = this.clock.instant();
		if (queueItem.getState() == QueueState.NEW) {
			queueItem.setState(QueueState.IN_REVIEW);
		}
		queueItem.setAssigneeAccountId(staffAccountId);
		queueItem.setUpdatedAt(now);
		QueueItem saved = this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(staffAccountId, "QUEUE_ITEM_CLAIMED", "QueueItem", queueItemId.toString(),
				"SUCCESS", UUID.randomUUID(), null, null);

		log.info("Queue item {} claimed by staff account {}", queueItemId, staffAccountId);
		return saved;
	}

	public QueueItem unclaim(Long queueItemId, Long actorAccountId) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot unclaim inactive queue item in state: " + queueItem.getState());
		}

		Instant now = this.clock.instant();
		queueItem.setAssigneeAccountId(null);
		if (queueItem.getState() == QueueState.IN_REVIEW) {
			queueItem.setState(QueueState.NEW);
		}
		queueItem.setUpdatedAt(now);
		QueueItem saved = this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(actorAccountId, "QUEUE_ITEM_UNCLAIMED", "QueueItem", queueItemId.toString(),
				"SUCCESS", UUID.randomUUID(), null, null);

		log.info("Queue item {} unclaimed by actor {}", queueItemId, actorAccountId);
		return saved;
	}

	public QueueItem reassign(Long queueItemId, Long newAssigneeAccountId, Long actorAccountId) {
		Objects.requireNonNull(queueItemId, "queueItemId must not be null");
		Objects.requireNonNull(newAssigneeAccountId, "newAssigneeAccountId must not be null");

		Account staff = this.accountRepository.findById(newAssigneeAccountId)
			.orElseThrow(() -> new IllegalArgumentException("Staff account not found: " + newAssigneeAccountId));
		if (staff.getRole() != Role.STAFF) {
			throw new IllegalArgumentException("Account is not a staff member: " + newAssigneeAccountId);
		}

		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));

		if (queueItem.getState() == QueueState.RESOLVED || queueItem.getState() == QueueState.CLOSED) {
			throw new IllegalStateException("Cannot reassign inactive queue item in state: " + queueItem.getState());
		}

		Instant now = this.clock.instant();
		queueItem.setAssigneeAccountId(newAssigneeAccountId);
		queueItem.setUpdatedAt(now);
		QueueItem saved = this.queueItemRepository.save(queueItem);

		this.auditService.recordEvent(actorAccountId, "QUEUE_ITEM_REASSIGNED", "QueueItem", queueItemId.toString(),
				"SUCCESS", UUID.randomUUID(), null, null);

		log.info("Queue item {} reassigned to staff account {} by actor {}", queueItemId, newAssigneeAccountId,
				actorAccountId);
		return saved;
	}

}
