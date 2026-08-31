package org.springframework.samples.petclinic.shared.command;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "command_records")
public class CommandRecord {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "actor_account_id")
	private Long actorAccountId;

	@Column(name = "action", nullable = false, length = 100)
	private String action;

	@Column(name = "target", nullable = false, length = 200)
	private String target;

	@Column(name = "request_hash", nullable = false, length = 128)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private CommandStatus status = CommandStatus.ISSUED;

	@Column(name = "result_status")
	private Integer resultStatus;

	@Column(name = "result_type", length = 100)
	private String resultType;

	@Column(name = "result_id", length = 100)
	private String resultId;

	@Column(name = "result_location", length = 500)
	private String resultLocation;

	@Column(name = "result_body", length = 4000)
	private String resultBody;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@PrePersist
	protected void onCreate() {
		if (this.createdAt == null) {
			this.createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return this.id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Long getActorAccountId() {
		return this.actorAccountId;
	}

	public void setActorAccountId(Long actorAccountId) {
		this.actorAccountId = actorAccountId;
	}

	public String getAction() {
		return this.action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getTarget() {
		return this.target;
	}

	public void setTarget(String target) {
		this.target = target;
	}

	public String getRequestHash() {
		return this.requestHash;
	}

	public void setRequestHash(String requestHash) {
		this.requestHash = requestHash;
	}

	public CommandStatus getStatus() {
		return this.status;
	}

	public void setStatus(CommandStatus status) {
		this.status = status;
	}

	public Integer getResultStatus() {
		return this.resultStatus;
	}

	public void setResultStatus(Integer resultStatus) {
		this.resultStatus = resultStatus;
	}

	public String getResultType() {
		return this.resultType;
	}

	public void setResultType(String resultType) {
		this.resultType = resultType;
	}

	public String getResultId() {
		return this.resultId;
	}

	public void setResultId(String resultId) {
		this.resultId = resultId;
	}

	public String getResultLocation() {
		return this.resultLocation;
	}

	public void setResultLocation(String resultLocation) {
		this.resultLocation = resultLocation;
	}

	public String getResultBody() {
		return this.resultBody;
	}

	public void setResultBody(String resultBody) {
		this.resultBody = resultBody;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

}
