package org.springframework.samples.petclinic.scheduling.model;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "operations")
public class OperationToken extends BaseEntity {

	@Column(name = "token", nullable = false, unique = true, length = 100)
	private String token;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "operation_type", nullable = false, length = 30)
	private OperationType operationType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private OperationStatus status = OperationStatus.DISPATCHED;

	@Column(name = "dispatched_at", nullable = false)
	private Instant dispatchedAt;

	@Column(name = "deadline", nullable = false)
	private Instant deadline;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 1;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	public OperationToken() {
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public OperationType getOperationType() {
		return operationType;
	}

	public void setOperationType(OperationType operationType) {
		this.operationType = operationType;
	}

	public OperationStatus getStatus() {
		return status;
	}

	public void setStatus(OperationStatus status) {
		this.status = status;
	}

	public Instant getDispatchedAt() {
		return dispatchedAt;
	}

	public void setDispatchedAt(Instant dispatchedAt) {
		this.dispatchedAt = dispatchedAt;
	}

	public Instant getDeadline() {
		return deadline;
	}

	public void setDeadline(Instant deadline) {
		this.deadline = deadline;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
