package org.springframework.samples.petclinic.scheduling.request;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "interpretation_windows")
public class InterpretationWindow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "interpretation_id", nullable = false)
	private Interpretation interpretation;

	@Column(name = "window_kind", nullable = false, length = 16)
	private String windowKind;

	@Enumerated(EnumType.STRING)
	@Column(name = "weekday", length = 16)
	private DayOfWeek weekday;

	@Column(name = "window_date")
	private LocalDate date;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	protected InterpretationWindow() {
	}

	public InterpretationWindow(String windowKind, DayOfWeek weekday, LocalDate date, LocalTime startTime,
			LocalTime endTime) {
		this.windowKind = windowKind;
		this.weekday = weekday;
		this.date = date;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public static InterpretationWindow preferred(DayOfWeek weekday, LocalTime start, LocalTime end) {
		return new InterpretationWindow("PREFERRED", weekday, null, start, end);
	}

	public static InterpretationWindow preferred(LocalDate date, LocalTime start, LocalTime end) {
		return new InterpretationWindow("PREFERRED", null, date, start, end);
	}

	public static InterpretationWindow allowed(DayOfWeek weekday, LocalTime start, LocalTime end) {
		return new InterpretationWindow("ALLOWED", weekday, null, start, end);
	}

	public static InterpretationWindow allowed(LocalDate date, LocalTime start, LocalTime end) {
		return new InterpretationWindow("ALLOWED", null, date, start, end);
	}

	public static InterpretationWindow excluded(DayOfWeek weekday, LocalTime start, LocalTime end) {
		return new InterpretationWindow("EXCLUDED", weekday, null, start, end);
	}

	public static InterpretationWindow excluded(LocalDate date, LocalTime start, LocalTime end) {
		return new InterpretationWindow("EXCLUDED", null, date, start, end);
	}

	void attachTo(Interpretation interpretation) {
		this.interpretation = interpretation;
	}

	public String getWindowKind() {
		return this.windowKind;
	}

	public DayOfWeek getWeekday() {
		return this.weekday;
	}

	public LocalDate getDate() {
		return this.date;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
