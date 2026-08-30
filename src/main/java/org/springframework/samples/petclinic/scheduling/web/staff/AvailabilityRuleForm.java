package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

public class AvailabilityRuleForm {

	private Integer expectedVersion;

	private Integer veterinarianId;

	private DayOfWeek dayOfWeek;

	private LocalTime startLocalTime;

	private LocalTime endLocalTime;

	private LocalDate startLocalDate;

	private LocalDate endLocalDate;

	private boolean releaseHolds;

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

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public void setDayOfWeek(DayOfWeek dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public LocalTime getStartLocalTime() {
		return this.startLocalTime;
	}

	public void setStartLocalTime(LocalTime startLocalTime) {
		this.startLocalTime = startLocalTime;
	}

	public LocalTime getEndLocalTime() {
		return this.endLocalTime;
	}

	public void setEndLocalTime(LocalTime endLocalTime) {
		this.endLocalTime = endLocalTime;
	}

	public LocalDate getStartLocalDate() {
		return this.startLocalDate;
	}

	public void setStartLocalDate(LocalDate startLocalDate) {
		this.startLocalDate = startLocalDate;
	}

	public LocalDate getEndLocalDate() {
		return this.endLocalDate;
	}

	public void setEndLocalDate(LocalDate endLocalDate) {
		this.endLocalDate = endLocalDate;
	}

	public boolean isReleaseHolds() {
		return this.releaseHolds;
	}

	public void setReleaseHolds(boolean releaseHolds) {
		this.releaseHolds = releaseHolds;
	}

}
