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
@Table(name = "clinic_hours")
public class ClinicHours {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "policy_id", nullable = false)
	private Long policyId;

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

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public LocalTime getStartLocalTime() {
		return this.startLocalTime;
	}

	public LocalTime getEndLocalTime() {
		return this.endLocalTime;
	}

	public Long getPolicyId() {
		return this.policyId;
	}

	public void setPolicyId(Long policyId) {
		this.policyId = policyId;
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
