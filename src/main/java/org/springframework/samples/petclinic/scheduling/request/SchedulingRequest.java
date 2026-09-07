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
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "scheduling_requests")
public class SchedulingRequest extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "pet_id", nullable = false)
	private Pet pet;

	@NotBlank
	@Size(min = 1, max = 2000)
	@Column(name = "request_text", nullable = false, length = 2000)
	private String requestText;

	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 32)
	private RequestState state;

	protected SchedulingRequest() {
	}

	public Pet getPet() {
		return this.pet;
	}

	public String getRequestText() {
		return this.requestText;
	}

	public RequestState getState() {
		return this.state;
	}

}
