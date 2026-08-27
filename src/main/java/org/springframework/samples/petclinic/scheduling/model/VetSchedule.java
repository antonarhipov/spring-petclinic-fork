package org.springframework.samples.petclinic.scheduling.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vet_schedules")
public class VetSchedule extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "day_of_week", nullable = false)
	private int dayOfWeek;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "shift_type", nullable = false, length = 20)
	private ShiftType shiftType = ShiftType.REGULAR;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	public VetSchedule() {
	}

	public Vet getVet() {
		return vet;
	}

	public void setVet(Vet vet) {
		this.vet = vet;
	}

	public int getDayOfWeekValue() {
		return dayOfWeek;
	}

	public void setDayOfWeekValue(int dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public DayOfWeek getDayOfWeek() {
		return DayOfWeek.of(this.dayOfWeek);
	}

	public void setDayOfWeek(DayOfWeek dayOfWeek) {
		this.dayOfWeek = (dayOfWeek != null) ? dayOfWeek.getValue() : 1;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public ShiftType getShiftType() {
		return shiftType;
	}

	public void setShiftType(ShiftType shiftType) {
		this.shiftType = shiftType;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

}
