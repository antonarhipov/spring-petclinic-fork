package org.springframework.samples.petclinic.scheduling.request;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "active_scheduling_requests")
public class ActiveSchedulingRequest {

	@Id
	@Column(name = "pet_id", nullable = false)
	private Integer petId;

	@Column(name = "request_id", nullable = false, unique = true)
	private Long requestId;

	public ActiveSchedulingRequest() {
	}

	public ActiveSchedulingRequest(Integer petId, Long requestId) {
		this.petId = Objects.requireNonNull(petId, "petId must not be null");
		this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
	}

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public Long getRequestId() {
		return this.requestId;
	}

	public void setRequestId(Long requestId) {
		this.requestId = requestId;
	}

}
