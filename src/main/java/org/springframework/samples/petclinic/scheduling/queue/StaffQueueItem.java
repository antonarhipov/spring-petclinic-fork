package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_queue_items")
public class StaffQueueItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false, unique = true)
	private Long requestId;

	@Column(nullable = false)
	private String priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private QueueState state;

	@Column(name = "assignee_account_id")
	private Long assigneeAccountId;

	@Column(name = "reason_code")
	private String reasonCode;

	@Column(name = "resolution_code")
	private String resolutionCode;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public String getPriority() {
		return this.priority;
	}

	public void setPriority(String priority) {
		this.priority = priority;
	}

	public QueueState getState() {
		return this.state;
	}

	public void setState(QueueState state) {
		this.state = state;
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

	public Long getAssigneeAccountId() {
		return this.assigneeAccountId;
	}

	public void setAssigneeAccountId(Long assigneeAccountId) {
		this.assigneeAccountId = assigneeAccountId;
	}

	public String getReasonCode() {
		return this.reasonCode;
	}

	public void setReasonCode(String reasonCode) {
		this.reasonCode = reasonCode;
	}

	public String getResolutionCode() {
		return this.resolutionCode;
	}

	public void setResolutionCode(String resolutionCode) {
		this.resolutionCode = resolutionCode;
	}

	public Integer getVersion() {
		return this.version;
	}

}
