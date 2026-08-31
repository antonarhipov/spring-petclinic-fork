package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.audit.ProtectedPayload;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;

@Entity
@Table(name = "workflow_revisions")
public class WorkflowRevision {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "revision_number", nullable = false)
	private Integer revisionNumber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "text_revision_id")
	private TextRevision textRevision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "interpretation_id")
	private Interpretation interpretation;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 64)
	private WorkflowRevisionState state;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reason_payload_id", nullable = false, unique = true)
	private ProtectedPayload reasonPayload;

	@Column(name = "duration_minutes", nullable = false)
	private Integer durationMinutes;

	@Column(name = "preferred_vet_id")
	private Integer preferredVetId;

	@Column(name = "required_specialty_id")
	private Integer requiredSpecialtyId;

	@Enumerated(EnumType.STRING)
	@Column(name = "urgency", nullable = false, length = 32)
	private Urgency urgency;

	@Column(name = "automatic_offer_count", nullable = false)
	private Integer automaticOfferCount = 0;

	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	@OneToMany(mappedBy = "workflowRevision", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<AvailabilityWindow> availabilityWindows = new ArrayList<>();

	@OneToMany(mappedBy = "workflowRevision", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OfferExclusion> exclusions = new ArrayList<>();

	public WorkflowRevision() {
	}

	public WorkflowRevision(SchedulingRequest request, Integer revisionNumber, TextRevision textRevision,
			Interpretation interpretation, WorkflowRevisionState state, ProtectedPayload reasonPayload,
			Integer durationMinutes, Integer preferredVetId, Integer requiredSpecialtyId, Urgency urgency,
			Integer automaticOfferCount) {
		this.request = Objects.requireNonNull(request, "request must not be null");
		this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber must not be null");
		this.textRevision = textRevision;
		this.interpretation = interpretation;
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.reasonPayload = Objects.requireNonNull(reasonPayload, "reasonPayload must not be null");
		this.durationMinutes = Objects.requireNonNull(durationMinutes, "durationMinutes must not be null");
		this.preferredVetId = preferredVetId;
		this.requiredSpecialtyId = requiredSpecialtyId;
		this.urgency = Objects.requireNonNull(urgency, "urgency must not be null");
		this.automaticOfferCount = (automaticOfferCount != null) ? automaticOfferCount : 0;
	}

	public WorkflowRevision(SchedulingRequest request, Integer revisionNumber, WorkflowRevisionState state,
			ProtectedPayload reasonPayload, Integer durationMinutes, Integer preferredVetId,
			Integer requiredSpecialtyId, Urgency urgency, Instant createdAt) {
		this(request, revisionNumber, request.getCurrentTextRevision(), null, state,
				reasonPayload != null ? reasonPayload : request.getCurrentTextRevision().getProsePayload(),
				durationMinutes, preferredVetId, requiredSpecialtyId, urgency, 0);
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

	public TextRevision getTextRevision() {
		return this.textRevision;
	}

	public void setTextRevision(TextRevision textRevision) {
		this.textRevision = textRevision;
	}

	public Interpretation getInterpretation() {
		return this.interpretation;
	}

	public void setInterpretation(Interpretation interpretation) {
		this.interpretation = interpretation;
	}

	public WorkflowRevisionState getState() {
		return this.state;
	}

	public void setState(WorkflowRevisionState state) {
		this.state = state;
	}

	public ProtectedPayload getReasonPayload() {
		return this.reasonPayload;
	}

	public void setReasonPayload(ProtectedPayload reasonPayload) {
		this.reasonPayload = reasonPayload;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Integer getPreferredVetId() {
		return this.preferredVetId;
	}

	public void setPreferredVetId(Integer preferredVetId) {
		this.preferredVetId = preferredVetId;
	}

	public Integer getRequiredSpecialtyId() {
		return this.requiredSpecialtyId;
	}

	public void setRequiredSpecialtyId(Integer requiredSpecialtyId) {
		this.requiredSpecialtyId = requiredSpecialtyId;
	}

	public Urgency getUrgency() {
		return this.urgency;
	}

	public void setUrgency(Urgency urgency) {
		this.urgency = urgency;
	}

	public Integer getAutomaticOfferCount() {
		return this.automaticOfferCount;
	}

	public void setAutomaticOfferCount(Integer automaticOfferCount) {
		this.automaticOfferCount = automaticOfferCount;
	}

	public Instant getConfirmedAt() {
		return this.confirmedAt;
	}

	public void setConfirmedAt(Instant confirmedAt) {
		this.confirmedAt = confirmedAt;
	}

	public List<AvailabilityWindow> getAvailabilityWindows() {
		return this.availabilityWindows;
	}

	public void setAvailabilityWindows(List<AvailabilityWindow> availabilityWindows) {
		this.availabilityWindows = availabilityWindows;
	}

	public void addAvailabilityWindow(AvailabilityWindow window) {
		this.availabilityWindows.add(window);
		window.setWorkflowRevision(this);
	}

	public List<OfferExclusion> getExclusions() {
		return this.exclusions;
	}

	public void setExclusions(List<OfferExclusion> exclusions) {
		this.exclusions = exclusions;
	}

	public void addExclusion(OfferExclusion exclusion) {
		this.exclusions.add(exclusion);
		exclusion.setWorkflowRevision(this);
	}

}
