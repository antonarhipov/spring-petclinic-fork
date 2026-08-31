package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.audit.ProtectedPayload;

@Entity
@Table(name = "text_revisions")
public class TextRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "revision_number", nullable = false)
	private Integer revisionNumber;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "prose_payload_id", nullable = false, unique = true)
	private ProtectedPayload prosePayload;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "consent_payload_id", nullable = false, unique = true)
	private ProtectedPayload consentPayload;

	public TextRevision() {
	}

	public TextRevision(SchedulingRequest request, Integer revisionNumber, Instant submittedAt,
			ProtectedPayload prosePayload, ProtectedPayload consentPayload) {
		this.request = Objects.requireNonNull(request, "request must not be null");
		this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber must not be null");
		this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
		this.prosePayload = Objects.requireNonNull(prosePayload, "prosePayload must not be null");
		this.consentPayload = Objects.requireNonNull(consentPayload, "consentPayload must not be null");
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

	public Integer getRevisionNumber() {
		return this.revisionNumber;
	}

	public Integer getVersion() {
		return this.revisionNumber;
	}

	public void setRevisionNumber(Integer revisionNumber) {
		this.revisionNumber = revisionNumber;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public ProtectedPayload getProsePayload() {
		return this.prosePayload;
	}

	public void setProsePayload(ProtectedPayload prosePayload) {
		this.prosePayload = prosePayload;
	}

	public ProtectedPayload getConsentPayload() {
		return this.consentPayload;
	}

	public void setConsentPayload(ProtectedPayload consentPayload) {
		this.consentPayload = consentPayload;
	}

}
