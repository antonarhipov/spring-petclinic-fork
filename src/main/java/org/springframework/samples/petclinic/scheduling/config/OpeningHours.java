package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_opening_hours")
public class OpeningHours {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "settings_id", nullable = false)
	private ClinicSettings settings;

	@Enumerated(EnumType.STRING)
	@Column(name = "weekday", nullable = false, length = 16)
	private DayOfWeek weekday;

	@Column(name = "open_time")
	private LocalTime openTime;

	@Column(name = "close_time")
	private LocalTime closeTime;

	public OpeningHours() {
	}

	public OpeningHours(ClinicSettings settings, DayOfWeek weekday, LocalTime openTime, LocalTime closeTime) {
		this.settings = settings;
		this.weekday = weekday;
		this.openTime = openTime;
		this.closeTime = closeTime;
	}

	public Integer getId() {
		return this.id;
	}

	public ClinicSettings getSettings() {
		return this.settings;
	}

	void attachTo(ClinicSettings settings) {
		this.settings = settings;
	}

	public DayOfWeek getWeekday() {
		return this.weekday;
	}

	public LocalTime getOpenTime() {
		return this.openTime;
	}

	public LocalTime getCloseTime() {
		return this.closeTime;
	}

	public boolean isOpen() {
		return this.openTime != null && this.closeTime != null;
	}

	public void update(LocalTime openTime, LocalTime closeTime) {
		this.openTime = openTime;
		this.closeTime = closeTime;
	}

}
