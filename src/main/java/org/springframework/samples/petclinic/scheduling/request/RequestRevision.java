package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "request_revisions")
public class RequestRevision extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "revision_number", nullable = false)
	private int revisionNumber;

	@Column(name = "source_text", nullable = false, length = 2000)
	private String sourceText;

	@Column(name = "consent_given", nullable = false)
	private boolean consentGiven;

	@Column(name = "consent_at")
	private Instant consentAt;

	@Column(name = "raw_interpretation", length = 4000)
	private String rawInterpretation;

	@Column(name = "model_identifier")
	private String modelIdentifier;

	@Column(name = "correlation_id", nullable = false)
	private String correlationId;

	@Column(name = "visit_reason", length = 1000)
	private String visitReason;

	@Column(name = "duration_minutes")
	private Integer durationMinutes;

	@Column(name = "care_type")
	private String careType;

	@Column(name = "required_specialty")
	private String requiredSpecialty;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "preferred_vet_id")
	private Vet preferredVet;

	@Column
	private String urgency;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RevisionStatus status;

	@OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<RequestAvailabilityWindow> availabilityWindows = new ArrayList<>();

	@Version
	private long version;

	protected RequestRevision() {
	}

	public RequestRevision(SchedulingRequest request, int revisionNumber, String sourceText, boolean consentGiven,
			Instant consentAt, String correlationId) {
		this.request = request;
		this.revisionNumber = revisionNumber;
		this.sourceText = sourceText;
		this.consentGiven = consentGiven;
		this.consentAt = consentAt;
		this.correlationId = correlationId;
		this.status = consentGiven ? RevisionStatus.AWAITING_CONFIRMATION : RevisionStatus.NEEDS_STAFF_REVIEW;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public int getRevisionNumber() {
		return this.revisionNumber;
	}

	public String getSourceText() {
		return this.sourceText;
	}

	public boolean isConsentGiven() {
		return this.consentGiven;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public String getVisitReason() {
		return this.visitReason;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public String getRequiredSpecialty() {
		return this.requiredSpecialty;
	}

	public Vet getPreferredVet() {
		return this.preferredVet;
	}

	public RevisionStatus getStatus() {
		return this.status;
	}

	public List<RequestAvailabilityWindow> getAvailabilityWindows() {
		return this.availabilityWindows;
	}

	public void applyInterpretation(String raw, String model, String reason, int duration, String careType,
			String specialty, Vet preferredVet, String urgency) {
		this.rawInterpretation = raw;
		this.modelIdentifier = model;
		this.visitReason = reason;
		this.durationMinutes = duration;
		this.careType = careType;
		this.requiredSpecialty = specialty;
		this.preferredVet = preferredVet;
		this.urgency = urgency;
		this.status = RevisionStatus.AWAITING_CONFIRMATION;
	}

	public void confirm(Instant at) {
		this.confirmedAt = at;
		this.status = RevisionStatus.CONFIRMED;
	}

	public void requireStaffReview() {
		this.status = RevisionStatus.NEEDS_STAFF_REVIEW;
	}

	public void supersede() {
		this.status = RevisionStatus.SUPERSEDED;
	}

	public void withdraw() {
		this.status = RevisionStatus.WITHDRAWN;
	}

	public void replaceWindows(List<RequestAvailabilityWindow> windows) {
		this.availabilityWindows.clear();
		windows.forEach(window -> {
			window.setRevision(this);
			this.availabilityWindows.add(window);
		});
	}

}
