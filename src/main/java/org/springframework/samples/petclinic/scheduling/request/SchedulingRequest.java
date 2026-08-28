package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Pet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SchedulingRequestState state;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "current_revision_id")
	private RequestRevision currentRevision;

	@Column(name = "emergency_priority", nullable = false)
	private boolean emergencyPriority;

	@Version
	private long version;

	protected SchedulingRequest() {
	}

	public SchedulingRequest(Pet pet, boolean emergencyPriority) {
		this.pet = pet;
		this.emergencyPriority = emergencyPriority;
		this.state = SchedulingRequestState.INTERPRETATION_REVIEW;
	}

	public Pet getPet() {
		return this.pet;
	}

	public SchedulingRequestState getState() {
		return this.state;
	}

	public RequestRevision getCurrentRevision() {
		return this.currentRevision;
	}

	public boolean isEmergencyPriority() {
		return this.emergencyPriority;
	}

	public void setCurrentRevision(RequestRevision revision) {
		this.currentRevision = revision;
	}

	public void moveTo(SchedulingRequestState state) {
		this.state = state;
	}

}
