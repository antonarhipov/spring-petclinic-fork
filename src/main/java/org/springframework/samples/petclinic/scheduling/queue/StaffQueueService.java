package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffQueueService {

	private final StaffQueueRepository queueItems;

	private final QueueNoteRepository notes;

	private final SchedulingRequestRepository requests;

	private final StaffQueueAuditService audit;

	private final Clock clock;

	public StaffQueueService(StaffQueueRepository queueItems, QueueNoteRepository notes,
			SchedulingRequestRepository requests, StaffQueueAuditService audit, Clock clock) {
		this.queueItems = queueItems;
		this.notes = notes;
		this.requests = requests;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<StaffQueueItem> openQueue() {
		return this.queueItems.findAll()
			.stream()
			.filter(item -> item.getState() != QueueState.CLOSED && item.getState() != QueueState.RESOLVED)
			.sorted(Comparator.comparing((StaffQueueItem item) -> !"EMERGENCY".equals(item.getPriority()))
				.thenComparing(item -> this.requests.findById(item.getRequestId())
					.map(SchedulingRequest::getCreatedAt)
					.orElse(Instant.MAX)))
			.toList();
	}

	@Transactional(readOnly = true)
	public StaffQueueItem require(Long id) {
		return this.queueItems.findById(id).orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional
	public void claim(Long itemId, Long staffAccountId, Integer expectedVersion) {
		StaffQueueItem item = require(itemId);
		assertVersion(item, expectedVersion);
		item.setAssigneeAccountId(staffAccountId);
		item.setState(QueueState.IN_REVIEW);
		item.setUpdatedAt(Instant.now(this.clock));
		this.audit.record(staffAccountId, "QUEUE_CLAIM", item.getRequestId(),
				this.audit.json("assignee", staffAccountId));
	}

	@Transactional
	public void unclaim(Long itemId, Long staffAccountId, Integer expectedVersion, String reason) {
		requireReason(reason);
		StaffQueueItem item = require(itemId);
		assertVersion(item, expectedVersion);
		item.setAssigneeAccountId(null);
		item.setState(QueueState.NEW);
		item.setUpdatedAt(Instant.now(this.clock));
		this.audit.record(staffAccountId, "QUEUE_UNCLAIM", item.getRequestId(), this.audit.json("reason", reason));
	}

	@Transactional
	public void reassign(Long itemId, Long staffAccountId, Long newAssigneeId, Integer expectedVersion, String reason) {
		requireReason(reason);
		StaffQueueItem item = require(itemId);
		assertVersion(item, expectedVersion);
		item.setAssigneeAccountId(newAssigneeId);
		item.setState(QueueState.IN_REVIEW);
		item.setUpdatedAt(Instant.now(this.clock));
		this.audit.record(staffAccountId, "QUEUE_REASSIGN", item.getRequestId(),
				this.audit.json("assignee", newAssigneeId, "reason", reason));
	}

	@Transactional
	public void addNote(Long itemId, Long staffAccountId, String body) {
		StaffQueueItem item = require(itemId);
		QueueNote note = new QueueNote();
		note.setQueueItemId(item.getId());
		note.setAuthorAccountId(staffAccountId);
		note.setBody(body);
		note.setCreatedAt(Instant.now(this.clock));
		this.notes.save(note);
		this.audit.record(staffAccountId, "QUEUE_NOTE", item.getRequestId(), null);
	}

	@Transactional(readOnly = true)
	public List<QueueNote> notes(Long itemId) {
		return this.notes.findByQueueItemIdOrderByCreatedAtAsc(itemId);
	}

	@Transactional
	public void close(Long itemId, Long staffAccountId, Integer expectedVersion, String resolution) {
		StaffQueueItem item = require(itemId);
		assertVersion(item, expectedVersion);
		Instant now = Instant.now(this.clock);
		item.setState(QueueState.CLOSED);
		item.setResolutionCode(resolution);
		item.setUpdatedAt(now);
		SchedulingRequest request = this.requests.findById(item.getRequestId()).orElseThrow();
		request.setState(RequestState.CLOSED);
		request.setOwnerStatusCode("CLOSED");
		request.setClosureOutcome(resolution);
		request.setClosedAt(now);
		request.setUpdatedAt(now);
		this.audit.record(staffAccountId, "QUEUE_CLOSE", item.getRequestId(),
				this.audit.json("resolution", resolution));
	}

	private void requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("REASON_REQUIRED");
		}
	}

	private void assertVersion(StaffQueueItem item, Integer expectedVersion) {
		if (expectedVersion != null && item.getVersion() != null && !expectedVersion.equals(item.getVersion())) {
			throw new StaleStateException("stale", item.getState().name(), null);
		}
	}

}
