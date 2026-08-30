package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
	@Column(nullable = false)
	private RequestState state;

	@Column(name = "owner_status_code", nullable = false)
	private String ownerStatusCode;

	@Column(name = "active_text_revision_id")
	private Long activeTextRevisionId;

	@Column(name = "active_request_revision_id")
	private Long activeRequestRevisionId;

	@Column(name = "suspected_emergency", nullable = false)
	private boolean suspectedEmergency;

	@Column(name = "closure_outcome")
	private String closureOutcome;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Version
	private Integer version;

	public Long getId() {
		return this.id;
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

	public String getOwnerStatusCode() {
		return this.ownerStatusCode;
	}

	public void setOwnerStatusCode(String ownerStatusCode) {
		this.ownerStatusCode = ownerStatusCode;
	}

	public Long getActiveTextRevisionId() {
		return this.activeTextRevisionId;
	}

	public void setActiveTextRevisionId(Long activeTextRevisionId) {
		this.activeTextRevisionId = activeTextRevisionId;
	}

	public Long getActiveRequestRevisionId() {
		return this.activeRequestRevisionId;
	}

	public void setActiveRequestRevisionId(Long activeRequestRevisionId) {
		this.activeRequestRevisionId = activeRequestRevisionId;
	}

	public boolean isSuspectedEmergency() {
		return this.suspectedEmergency;
	}

	public void setSuspectedEmergency(boolean suspectedEmergency) {
		this.suspectedEmergency = suspectedEmergency;
	}

	public String getClosureOutcome() {
		return this.closureOutcome;
	}

	public void setClosureOutcome(String closureOutcome) {
		this.closureOutcome = closureOutcome;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
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

	public Integer getVersion() {
		return this.version;
	}

}
