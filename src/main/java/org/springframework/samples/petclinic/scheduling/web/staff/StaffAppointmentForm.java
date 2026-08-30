package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.Instant;

public class StaffAppointmentForm {

	private Integer expectedVersion;

	private Integer veterinarianId;

	private Instant startAt;

	private Instant endAt;

	private String reasonCategory;

	private String note;

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public void setVeterinarianId(Integer veterinarianId) {
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

	public String getReasonCategory() {
		return this.reasonCategory;
	}

	public void setReasonCategory(String reasonCategory) {
		this.reasonCategory = reasonCategory;
	}

	public String getNote() {
		return this.note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
