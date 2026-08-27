package org.springframework.samples.petclinic.scheduling.model;

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
@Table(name = "request_availability_windows")
public class RequestAvailabilityWindow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Column(name = "window_order", nullable = false)
	private int windowOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "window_kind", nullable = false, length = 20)
	private AvailabilityWindowKind kind;

	@Enumerated(EnumType.STRING)
	@Column(name = "expression_type", nullable = false, length = 30)
	private CalendarExpressionType expressionType;

	@Column(name = "explicit_date")
	private LocalDate explicitDate;

	@Column(name = "day_of_week")
	private Integer dayOfWeek;

	@Column(name = "week_offset")
	private Integer weekOffset;

	@Column(name = "month_offset")
	private Integer monthOffset;

	@Column(name = "month_of_year")
	private Integer monthOfYear;

	@Enumerated(EnumType.STRING)
	@Column(name = "ordinal_week", length = 20)
	private OrdinalWeek ordinalWeek;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "named_period", length = 20)
	private String namedPeriod;

	public SchedulingRequest getRequest() {
		return request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public int getWindowOrder() {
		return windowOrder;
	}

	public void setWindowOrder(int windowOrder) {
		this.windowOrder = windowOrder;
	}

	public AvailabilityWindowKind getKind() {
		return kind;
	}

	public void setKind(AvailabilityWindowKind kind) {
		this.kind = kind;
	}

	public CalendarExpressionType getExpressionType() {
		return expressionType;
	}

	public void setExpressionType(CalendarExpressionType expressionType) {
		this.expressionType = expressionType;
	}

	public LocalDate getExplicitDate() {
		return explicitDate;
	}

	public void setExplicitDate(LocalDate explicitDate) {
		this.explicitDate = explicitDate;
	}

	public Integer getDayOfWeek() {
		return dayOfWeek;
	}

	public void setDayOfWeek(Integer dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public Integer getWeekOffset() {
		return weekOffset;
	}

	public void setWeekOffset(Integer weekOffset) {
		this.weekOffset = weekOffset;
	}

	public Integer getMonthOffset() {
		return monthOffset;
	}

	public void setMonthOffset(Integer monthOffset) {
		this.monthOffset = monthOffset;
	}

	public Integer getMonthOfYear() {
		return monthOfYear;
	}

	public void setMonthOfYear(Integer monthOfYear) {
		this.monthOfYear = monthOfYear;
	}

	public OrdinalWeek getOrdinalWeek() {
		return ordinalWeek;
	}

	public void setOrdinalWeek(OrdinalWeek ordinalWeek) {
		this.ordinalWeek = ordinalWeek;
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

	public String getNamedPeriod() {
		return namedPeriod;
	}

	public void setNamedPeriod(String namedPeriod) {
		this.namedPeriod = namedPeriod;
	}

}
