package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_settings")
public class ClinicSettings {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "booking_horizon_days", nullable = false)
	private int bookingHorizonDays;

	@Column(name = "minimum_lead_days", nullable = false)
	private int minimumLeadDays;

	@Column(name = "minimum_duration_minutes", nullable = false)
	private int minimumDurationMinutes;

	@Column(name = "default_duration_minutes", nullable = false)
	private int defaultDurationMinutes;

	@Column(name = "maximum_duration_minutes", nullable = false)
	private int maximumDurationMinutes;

	@Column(name = "grid_minutes", nullable = false)
	private int gridMinutes;

	@Column(name = "morning_start", nullable = false)
	private LocalTime morningStart;

	@Column(name = "morning_end", nullable = false)
	private LocalTime morningEnd;

	@Column(name = "afternoon_start", nullable = false)
	private LocalTime afternoonStart;

	@Column(name = "afternoon_end", nullable = false)
	private LocalTime afternoonEnd;

	@Column(name = "evening_start", nullable = false)
	private LocalTime eveningStart;

	@Column(name = "evening_end", nullable = false)
	private LocalTime eveningEnd;

	@Column(name = "time_zone", nullable = false, length = 80)
	private String timeZone;

	@OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OpeningHours> openingHours = new ArrayList<>();

	public ClinicSettings() {
	}

	public Integer getId() {
		return this.id;
	}

	public int getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public int getMinimumLeadDays() {
		return this.minimumLeadDays;
	}

	public int getMinimumDurationMinutes() {
		return this.minimumDurationMinutes;
	}

	public int getDefaultDurationMinutes() {
		return this.defaultDurationMinutes;
	}

	public int getMaximumDurationMinutes() {
		return this.maximumDurationMinutes;
	}

	public int getGridMinutes() {
		return this.gridMinutes;
	}

	public LocalTime getMorningStart() {
		return this.morningStart;
	}

	public LocalTime getMorningEnd() {
		return this.morningEnd;
	}

	public LocalTime getAfternoonStart() {
		return this.afternoonStart;
	}

	public LocalTime getAfternoonEnd() {
		return this.afternoonEnd;
	}

	public LocalTime getEveningStart() {
		return this.eveningStart;
	}

	public LocalTime getEveningEnd() {
		return this.eveningEnd;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public List<OpeningHours> getOpeningHours() {
		return Collections.unmodifiableList(this.openingHours);
	}

	public Optional<OpeningHours> getOpeningHoursFor(DayOfWeek weekday) {
		return this.openingHours.stream().filter(oh -> oh.getWeekday() == weekday).findFirst();
	}

	public void addOpeningHours(OpeningHours oh) {
		oh.attachTo(this);
		this.openingHours.add(oh);
	}

	public void update(int bookingHorizonDays, int minimumLeadDays, int minimumDurationMinutes,
			int defaultDurationMinutes, int maximumDurationMinutes, String timeZone) {
		this.bookingHorizonDays = bookingHorizonDays;
		this.minimumLeadDays = minimumLeadDays;
		this.minimumDurationMinutes = minimumDurationMinutes;
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.maximumDurationMinutes = maximumDurationMinutes;
		this.timeZone = timeZone;
	}

	public void replaceOpeningHours(List<OpeningHours> values) {
		this.openingHours.clear();
		values.forEach(this::addOpeningHours);
	}

}
