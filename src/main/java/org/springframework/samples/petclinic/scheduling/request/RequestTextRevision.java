package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_text_revisions")
public class RequestTextRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private Long requestId;

	@Column(nullable = false)
	private int sequence;

	@Column(name = "source_text", nullable = false, length = 2000)
	private String sourceText;

	@Column(name = "source_hash", nullable = false, length = 64)
	private String sourceHash;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "clinic_zone_id", nullable = false)
	private String clinicZoneId;

	@Column(name = "emergency_screen_version", nullable = false)
	private String emergencyScreenVersion;

	@Column(name = "emergency_matched_terms_json")
	private String emergencyMatchedTermsJson;

	public Long getId() {
		return this.id;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

	public int getSequence() {
		return this.sequence;
	}

	public void setSequence(int sequence) {
		this.sequence = sequence;
	}

	public String getSourceText() {
		return this.sourceText;
	}

	public void setSourceText(String sourceText) {
		this.sourceText = sourceText;
	}

	public String getSourceHash() {
		return this.sourceHash;
	}

	public void setSourceHash(String sourceHash) {
		this.sourceHash = sourceHash;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public String getClinicZoneId() {
		return this.clinicZoneId;
	}

	public void setClinicZoneId(String clinicZoneId) {
		this.clinicZoneId = clinicZoneId;
	}

	public String getEmergencyScreenVersion() {
		return this.emergencyScreenVersion;
	}

	public void setEmergencyScreenVersion(String emergencyScreenVersion) {
		this.emergencyScreenVersion = emergencyScreenVersion;
	}

	public String getEmergencyMatchedTermsJson() {
		return this.emergencyMatchedTermsJson;
	}

	public void setEmergencyMatchedTermsJson(String emergencyMatchedTermsJson) {
		this.emergencyMatchedTermsJson = emergencyMatchedTermsJson;
	}

}
