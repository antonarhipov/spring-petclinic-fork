package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "integration_executions")
public class IntegrationExecution {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String kind;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(name = "text_revision_id")
	private Long textRevisionId;

	@Column(name = "request_revision_id")
	private Long requestRevisionId;

	@Column(name = "trigger_key", nullable = false, unique = true)
	private String triggerKey;

	@Column(nullable = false)
	private String state;

	@Column(name = "triggered_at", nullable = false)
	private Instant triggeredAt;

	@Column(name = "deadline_at", nullable = false)
	private Instant deadlineAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "schema_version", nullable = false)
	private String schemaVersion;

	@Column(name = "requested_model_id")
	private String requestedModelId;

	@Column(name = "resolved_model_id")
	private String resolvedModelId;

	@Column(name = "prompt_template_version")
	private String promptTemplateVersion;

	@Column(name = "input_json")
	private String inputJson;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "outcome")
	private String outcome;

	@Version
	private Integer version;

	@OneToMany(mappedBy = "execution", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<IntegrationAttempt> attempts = new ArrayList<>();

	public UUID getId() {
		return this.id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getKind() {
		return this.kind;
	}

	public void setKind(String kind) {
		this.kind = kind;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public Long getTextRevisionId() {
		return this.textRevisionId;
	}

	public void setTextRevisionId(Long textRevisionId) {
		this.textRevisionId = textRevisionId;
	}

	public Long getRequestRevisionId() {
		return this.requestRevisionId;
	}

	public void setRequestRevisionId(Long requestRevisionId) {
		this.requestRevisionId = requestRevisionId;
	}

	public String getTriggerKey() {
		return this.triggerKey;
	}

	public void setTriggerKey(String triggerKey) {
		this.triggerKey = triggerKey;
	}

	public String getState() {
		return this.state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Instant getTriggeredAt() {
		return this.triggeredAt;
	}

	public void setTriggeredAt(Instant triggeredAt) {
		this.triggeredAt = triggeredAt;
	}

	public Instant getDeadlineAt() {
		return this.deadlineAt;
	}

	public void setDeadlineAt(Instant deadlineAt) {
		this.deadlineAt = deadlineAt;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getFinishedAt() {
		return this.finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

	public String getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getRequestedModelId() {
		return this.requestedModelId;
	}

	public void setRequestedModelId(String requestedModelId) {
		this.requestedModelId = requestedModelId;
	}

	public String getResolvedModelId() {
		return this.resolvedModelId;
	}

	public void setResolvedModelId(String resolvedModelId) {
		this.resolvedModelId = resolvedModelId;
	}

	public String getPromptTemplateVersion() {
		return this.promptTemplateVersion;
	}

	public void setPromptTemplateVersion(String promptTemplateVersion) {
		this.promptTemplateVersion = promptTemplateVersion;
	}

	public String getInputJson() {
		return this.inputJson;
	}

	public void setInputJson(String inputJson) {
		this.inputJson = inputJson;
	}

	public int getAttemptCount() {
		return this.attemptCount;
	}

	public void setAttemptCount(int attemptCount) {
		this.attemptCount = attemptCount;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public Integer getVersion() {
		return this.version;
	}

	public List<IntegrationAttempt> getAttempts() {
		return this.attempts;
	}

}
