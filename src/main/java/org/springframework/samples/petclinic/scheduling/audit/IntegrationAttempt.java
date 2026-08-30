package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "integration_attempts")
public class IntegrationAttempt {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "execution_id", nullable = false)
	private IntegrationExecution execution;

	@Column(nullable = false)
	private int sequence;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "input_json", nullable = false)
	private String inputJson;

	@Column(name = "raw_output")
	private String rawOutput;

	@Column
	private String outcome;

	@Column(name = "error_classification")
	private String errorClassification;

	public Long getId() {
		return this.id;
	}

	public IntegrationExecution getExecution() {
		return this.execution;
	}

	public void setExecution(IntegrationExecution execution) {
		this.execution = execution;
	}

	public int getSequence() {
		return this.sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
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

	public String getInputJson() {
		return this.inputJson;
	}

	public void setInputJson(String inputJson) {
		this.inputJson = inputJson;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public String getRawOutput() {
		return this.rawOutput;
	}

	public void setRawOutput(String rawOutput) {
		this.rawOutput = rawOutput;
	}

	public String getErrorClassification() {
		return this.errorClassification;
	}

	public void setErrorClassification(String errorClassification) {
		this.errorClassification = errorClassification;
	}

}
