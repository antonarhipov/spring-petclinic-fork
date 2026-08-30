package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "interpretation_records")
public class InterpretationRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "text_revision_id", nullable = false)
	private Long textRevisionId;

	@Column(nullable = false)
	private String origin;

	@Column(name = "schema_version")
	private String schemaVersion;

	@Column(name = "recognized_output_json")
	private String recognizedOutputJson;

	@Column(name = "unknown_fields_json")
	private String unknownFieldsJson;

	@Column(name = "uncertainties_json")
	private String uncertaintiesJson;

	@Column(name = "validation_issues_json")
	private String validationIssuesJson;

	@Column(nullable = false)
	private String outcome;

	@Column(name = "created_by_account_id")
	private Long createdByAccountId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Long getId() {
		return this.id;
	}

	public Long getTextRevisionId() {
		return this.textRevisionId;
	}

	public void setTextRevisionId(Long textRevisionId) {
		this.textRevisionId = textRevisionId;
	}

	public String getOrigin() {
		return this.origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getSchemaVersion() {
		return this.schemaVersion;
	}

	public void setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getRecognizedOutputJson() {
		return this.recognizedOutputJson;
	}

	public void setRecognizedOutputJson(String recognizedOutputJson) {
		this.recognizedOutputJson = recognizedOutputJson;
	}

	public String getUnknownFieldsJson() {
		return this.unknownFieldsJson;
	}

	public void setUnknownFieldsJson(String unknownFieldsJson) {
		this.unknownFieldsJson = unknownFieldsJson;
	}

	public String getUncertaintiesJson() {
		return this.uncertaintiesJson;
	}

	public void setUncertaintiesJson(String uncertaintiesJson) {
		this.uncertaintiesJson = uncertaintiesJson;
	}

	public String getValidationIssuesJson() {
		return this.validationIssuesJson;
	}

	public void setValidationIssuesJson(String validationIssuesJson) {
		this.validationIssuesJson = validationIssuesJson;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public void setOutcome(String outcome) {
		this.outcome = outcome;
	}

	public Long getCreatedByAccountId() {
		return this.createdByAccountId;
	}

	public void setCreatedByAccountId(Long createdByAccountId) {
		this.createdByAccountId = createdByAccountId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
