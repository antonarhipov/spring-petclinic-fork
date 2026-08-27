package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Instant;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;

public class AppointmentFact {

	private final Integer id;

	private final Integer vetId;

	private final Integer ownerId;

	private final Integer petId;

	private final Instant startTime;

	private final Instant endTime;

	private final AppointmentStatus status;

	public AppointmentFact(Integer id, Integer vetId, Integer ownerId, Integer petId, Instant startTime,
			Instant endTime, AppointmentStatus status) {
		this.id = id;
		this.vetId = vetId;
		this.ownerId = ownerId;
		this.petId = petId;
		this.startTime = startTime;
		this.endTime = endTime;
		this.status = status;
	}

	public Integer getId() {
		return id;
	}

	public Integer getVetId() {
		return vetId;
	}

	public Integer getOwnerId() {
		return ownerId;
	}

	public Integer getPetId() {
		return petId;
	}

	public Instant getStartTime() {
		return startTime;
	}

	public Instant getEndTime() {
		return endTime;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

}
