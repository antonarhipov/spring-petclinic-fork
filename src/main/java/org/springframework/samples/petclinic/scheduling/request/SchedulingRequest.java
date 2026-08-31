package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.Objects;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false)
	private Integer ownerId;

	@Column(name = "pet_id", nullable = false)
	private Integer petId;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 64)
	private RequestState state;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_text_revision_id")
	private TextRevision currentTextRevision;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_workflow_revision_id")
	private WorkflowRevision currentWorkflowRevision;

	@Column(name = "appointment_id", unique = true)
	private Long appointmentId;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	public SchedulingRequest() {
	}

	public SchedulingRequest(Integer ownerId, Integer petId, RequestState state, Instant submittedAt) {
		this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
		this.petId = Objects.requireNonNull(petId, "petId must not be null");
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt must not be null");
		this.updatedAt = submittedAt;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(Integer ownerId) {
		this.ownerId = ownerId;
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public RequestState getState() {
		return this.state;
	}

	public void setState(RequestState state) {
		this.state = state;
	}

	public TextRevision getCurrentTextRevision() {
		return this.currentTextRevision;
	}

	public void setCurrentTextRevision(TextRevision currentTextRevision) {
		this.currentTextRevision = currentTextRevision;
	}

	public WorkflowRevision getCurrentWorkflowRevision() {
		return this.currentWorkflowRevision;
	}

	public void setCurrentWorkflowRevision(WorkflowRevision currentWorkflowRevision) {
		this.currentWorkflowRevision = currentWorkflowRevision;
	}

	public Long getAppointmentId() {
		return this.appointmentId;
	}

	public void setAppointmentId(Long appointmentId) {
		this.appointmentId = appointmentId;
	}

	public Long getVersion() {
		return this.version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public void setSubmittedAt(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public Instant getUpdatedAt() {
		return this.updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getClosedAt() {
		return this.closedAt;
	}

	public void setClosedAt(Instant closedAt) {
		this.closedAt = closedAt;
	}

}
