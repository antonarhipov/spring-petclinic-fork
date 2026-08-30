package org.springframework.samples.petclinic.scheduling.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "active_pet_requests")
public class ActivePetRequest {

	@Id
	@Column(name = "pet_id")
	private Integer petId;

	@Column(name = "request_id", nullable = false, unique = true)
	private Long requestId;

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
