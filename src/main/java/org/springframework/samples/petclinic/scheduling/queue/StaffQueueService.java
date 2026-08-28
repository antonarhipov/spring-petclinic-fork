package org.springframework.samples.petclinic.scheduling.queue;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.audit.AuditAction;
import org.springframework.samples.petclinic.scheduling.audit.SchedulingAuditService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;

@Service
public class StaffQueueService {

	private final StaffQueueRepository queue;

	private final AccountRepository accounts;

	private final SchedulingAuditService audit;

	public StaffQueueService(StaffQueueRepository queue, AccountRepository accounts, SchedulingAuditService audit) {
		this.queue = queue;
		this.accounts = accounts;
		this.audit = audit;
	}

	@Transactional
	public StaffQueueItem claim(Integer itemId, Authentication actor) {
		StaffQueueItem item = item(itemId);
		Account account = this.accounts.findByUsername(actor.getName()).orElseThrow();
		item.claim(account);
		this.audit.record(actor, null, AuditAction.QUEUE_CLAIMED, "queue", itemId, null, "IN_REVIEW", null);
		return item;
	}

	@Transactional
	public StaffQueueItem reassign(Integer itemId, Integer accountId, String reason, Authentication actor) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reassignment reason is required");
		}
		StaffQueueItem item = item(itemId);
		if (accountId == null) {
			item.unclaim();
		}
		else {
			item.claim(this.accounts.findById(accountId).orElseThrow());
		}
		this.audit.record(actor, null, AuditAction.QUEUE_REASSIGNED, "queue", itemId, null, "updated", reason);
		return item;
	}

	@Transactional
	public StaffQueueItem resolve(Integer itemId, String resolution, Authentication actor) {
		StaffQueueItem item = item(itemId);
		item.resolve(resolution);
		this.audit.record(actor, null, AuditAction.QUEUE_RESOLVED, "queue", itemId, "IN_REVIEW", "RESOLVED",
				resolution);
		return item;
	}

	private StaffQueueItem item(Integer id) {
		return this.queue.findById(id).orElseThrow(() -> new IllegalArgumentException("Queue item not found"));
	}

}
