package org.springframework.samples.petclinic.scheduling.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_recurring_shifts")
public class VetRecurringShift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "veterinarian_id", nullable = false)
	private Integer veterinarianId;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", nullable = false)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_local_time", nullable = false)
	private LocalTime startLocalTime;

	@Column(name = "end_local_time", nullable = false)
	private LocalTime endLocalTime;

	public Long getId() {
		return this.id;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public LocalTime getStartLocalTime() {
		return this.startLocalTime;
	}

	public LocalTime getEndLocalTime() {
		return this.endLocalTime;
	}

	public void setVeterinarianId(Integer veterinarianId) {
		this.veterinarianId = veterinarianId;
	}

	public void setDayOfWeek(DayOfWeek dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public void setStartLocalTime(LocalTime startLocalTime) {
		this.startLocalTime = startLocalTime;
	}

	public void setEndLocalTime(LocalTime endLocalTime) {
		this.endLocalTime = endLocalTime;
	}

}
