package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;

@Entity
@Table(name = "queue_items")
public class QueueItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false, unique = true)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_revision_id")
	private WorkflowRevision workflowRevision;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false)
	private QueueState state;

	@Enumerated(EnumType.STRING)
	@Column(name = "awaiting_reason")
	private AwaitingReason awaitingReason;

	@Column(name = "fallback_reason", nullable = false)
	private String fallbackReason;

	@Enumerated(EnumType.STRING)
	@Column(name = "urgency", nullable = false)
	private Urgency urgency;

	@Column(name = "assignee_account_id")
	private Long assigneeAccountId;

	@Column(name = "last_contact_at")
	private Instant lastContactAt;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@OneToMany(mappedBy = "queueItem", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("attemptedAt ASC")
	private List<ContactAttempt> contactAttempts = new ArrayList<>();

	protected QueueItem() {
	}

	public QueueItem(SchedulingRequest request, WorkflowRevision workflowRevision, QueueState state,
			String fallbackReason, Urgency urgency, Instant now) {
		this.request = request;
		this.workflowRevision = workflowRevision;
		this.state = state;
		this.fallbackReason = fallbackReason;
		this.urgency = urgency;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public WorkflowRevision getWorkflowRevision() {
		return this.workflowRevision;
	}

	public void setWorkflowRevision(WorkflowRevision workflowRevision) {
		this.workflowRevision = workflowRevision;
	}

	public QueueState getState() {
		return this.state;
	}

	public void setState(QueueState state) {
		this.state = state;
	}

	public AwaitingReason getAwaitingReason() {
		return this.awaitingReason;
	}

	public void setAwaitingReason(AwaitingReason awaitingReason) {
		this.awaitingReason = awaitingReason;
	}

	public String getFallbackReason() {
		return this.fallbackReason;
	}

	public void setFallbackReason(String fallbackReason) {
		this.fallbackReason = fallbackReason;
	}

	public Urgency getUrgency() {
		return this.urgency;
	}

	public void setUrgency(Urgency urgency) {
		this.urgency = urgency;
	}

	public Long getAssigneeAccountId() {
		return this.assigneeAccountId;
	}

	public void setAssigneeAccountId(Long assigneeAccountId) {
		this.assigneeAccountId = assigneeAccountId;
	}

	public Instant getLastContactAt() {
		return this.lastContactAt;
	}

	public void setLastContactAt(Instant lastContactAt) {
		this.lastContactAt = lastContactAt;
	}

	public Long getVersion() {
		return this.version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getResolvedAt() {
		return this.resolvedAt;
	}

	public void setResolvedAt(Instant resolvedAt) {
		this.resolvedAt = resolvedAt;
	}

	public Instant getClosedAt() {
		return this.closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

	public List<ContactAttempt> getContactAttempts() {
		return this.contactAttempts;
	}

	public void addContactAttempt(ContactAttempt attempt) {
		this.contactAttempts.add(attempt);
		attempt.setQueueItem(this);
	}

	public void requireAssignedTo(Long actorAccountId) {
		// Claiming is no longer required; any authorized clinic staff or admin can
		// perform actions.
	}

	public void requireExpectedVersions(Long expectedRequestVersion, Integer expectedWorkflowRevision,
			Long expectedQueueVersion) {
		Integer currentWorkflowRevision = this.workflowRevision != null ? this.workflowRevision.getRevisionNumber()
				: (this.request.getCurrentWorkflowRevision() != null
						? this.request.getCurrentWorkflowRevision().getRevisionNumber() : null);
		if (!Objects.equals(this.request.getVersion(), expectedRequestVersion)
				|| !Objects.equals(currentWorkflowRevision, expectedWorkflowRevision)
				|| !Objects.equals(this.version, expectedQueueVersion)) {
			throw new IllegalStateException("This queue item changed while you were editing it. Reload and try again.");
		}
	}

}
