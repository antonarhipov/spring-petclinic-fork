package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_operating_intervals")
public class ClinicOperatingInterval {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "policy_id", nullable = false)
	private ClinicPolicy policy;

	@Enumerated(EnumType.STRING)
	@Column(name = "weekday", nullable = false, length = 16)
	private DayOfWeek weekday;

	@Column(name = "local_start", nullable = false)
	private LocalTime localStart;

	@Column(name = "local_end", nullable = false)
	private LocalTime localEnd;

	public ClinicOperatingInterval() {
	}

	public ClinicOperatingInterval(DayOfWeek weekday, LocalTime localStart, LocalTime localEnd) {
		this.weekday = weekday;
		this.localStart = localStart;
		this.localEnd = localEnd;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public ClinicPolicy getPolicy() {
		return this.policy;
	}

	public void setPolicy(ClinicPolicy policy) {
		this.policy = policy;
	}

	public DayOfWeek getWeekday() {
		return this.weekday;
	}

	public void setWeekday(DayOfWeek weekday) {
		this.weekday = weekday;
	}

	public LocalTime getLocalStart() {
		return this.localStart;
	}

	public void setLocalStart(LocalTime localStart) {
		this.localStart = localStart;
	}

	public LocalTime getLocalEnd() {
		return this.localEnd;
	}

	public void setLocalEnd(LocalTime localEnd) {
		this.localEnd = localEnd;
	}

}
