package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.Instant;

public class StaffOfferForm {

	private int veterinarianId;

	private Instant startAt;

	private Instant endAt;

	public int getVeterinarianId() {
		return this.veterinarianId;
	}

	public void setVeterinarianId(int veterinarianId) {
		this.veterinarianId = veterinarianId;
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

}
