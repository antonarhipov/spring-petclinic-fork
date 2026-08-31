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
import jakarta.persistence.Table;

@Entity
@Table(name = "offer_exclusions")
public class OfferExclusion {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_revision_id", nullable = false)
	private WorkflowRevision workflowRevision;

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(name = "end_at", nullable = false)
	private Instant endAt;

	@Column(name = "offer_id")
	private Long offerId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public OfferExclusion() {
	}

	public OfferExclusion(WorkflowRevision workflowRevision, Integer vetId, Instant startAt, Instant endAt,
			Long offerId, Instant createdAt) {
		this.workflowRevision = workflowRevision;
		this.vetId = Objects.requireNonNull(vetId, "vetId must not be null");
		this.startAt = Objects.requireNonNull(startAt, "startAt must not be null");
		this.endAt = Objects.requireNonNull(endAt, "endAt must not be null");
		this.offerId = offerId;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public WorkflowRevision getWorkflowRevision() {
		return this.workflowRevision;
	}

	public void setWorkflowRevision(WorkflowRevision workflowRevision) {
		this.workflowRevision = workflowRevision;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public Instant getStartAt() {
		return this.startAt;
	}

	public void setStartAt(Instant startAt) {
		this.startAt = startAt;
	}

	public Instant getEndAt() {
		return this.endAt;
	}

	public void setEndAt(Instant endAt) {
		this.endAt = endAt;
	}

	public Long getOfferId() {
		return this.offerId;
	}

	public void setOfferId(Long offerId) {
		this.offerId = offerId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
