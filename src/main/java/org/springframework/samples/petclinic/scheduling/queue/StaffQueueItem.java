package org.springframework.samples.petclinic.scheduling.queue;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.security.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_queue_items")
public class StaffQueueItem extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private QueueState state = QueueState.NEW;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private QueuePriority priority;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "claimed_by_account_id")
	private Account claimedBy;

	@Column(name = "resolution_details")
	private String resolutionDetails;

	@Version
	private long version;

	protected StaffQueueItem() {
	}

	public StaffQueueItem(SchedulingRequest request, QueuePriority priority) {
		this.request = request;
		this.priority = priority;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public QueueState getState() {
		return this.state;
	}

	public QueuePriority getPriority() {
		return this.priority;
	}

	public Account getClaimedBy() {
		return this.claimedBy;
	}

	public void claim(Account account) {
		this.claimedBy = account;
		this.state = QueueState.IN_REVIEW;
	}

	public void unclaim() {
		this.claimedBy = null;
		this.state = QueueState.NEW;
	}

	public void resolve(String details) {
		if (details == null || details.isBlank()) {
			throw new IllegalArgumentException("A queue resolution is required");
		}
		this.resolutionDetails = details;
		this.state = QueueState.RESOLVED;
	}

	public void close(String details) {
		this.resolutionDetails = details;
		this.state = QueueState.CLOSED;
	}

}
