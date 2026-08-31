package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public final class AvailabilityForms {

	private AvailabilityForms() {
	}

	public static class ShiftForm {

		private Long id;

		private Integer vetId;

		private DayOfWeek weekday = DayOfWeek.MONDAY;

		@DateTimeFormat(pattern = "HH:mm")
		private LocalTime localStart = LocalTime.of(9, 0);

		@DateTimeFormat(pattern = "HH:mm")
		private LocalTime localEnd = LocalTime.of(17, 0);

		public Long getId() {
			return this.id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
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

	public static class ExceptionDayForm {

		private Integer vetId;

		@DateTimeFormat(pattern = "yyyy-MM-dd")
		private LocalDate date;

		private boolean unavailableAllDay;

		@DateTimeFormat(pattern = "HH:mm")
		private LocalTime localStart;

		@DateTimeFormat(pattern = "HH:mm")
		private LocalTime localEnd;

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
		}

		public LocalDate getDate() {
			return this.date;
		}

		public void setDate(LocalDate date) {
			this.date = date;
		}

		public boolean isUnavailableAllDay() {
			return this.unavailableAllDay;
		}

		public void setUnavailableAllDay(boolean unavailableAllDay) {
			this.unavailableAllDay = unavailableAllDay;
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

	public static class LeaveForm {

		private Integer vetId;

		@DateTimeFormat(pattern = "yyyy-MM-dd")
		private LocalDate startDate;

		@DateTimeFormat(pattern = "yyyy-MM-dd")
		private LocalDate endDate;

		private String reasonCategory = "VACATION";

		private String internalNote;

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
		}

		public LocalDate getStartDate() {
			return this.startDate;
		}

		public void setStartDate(LocalDate startDate) {
			this.startDate = startDate;
		}

		public LocalDate getEndDate() {
			return this.endDate;
		}

		public void setEndDate(LocalDate endDate) {
			this.endDate = endDate;
		}

		public String getReasonCategory() {
			return this.reasonCategory;
		}

		public void setReasonCategory(String reasonCategory) {
			this.reasonCategory = reasonCategory;
		}

		public String getInternalNote() {
			return this.internalNote;
		}

		public void setInternalNote(String internalNote) {
			this.internalNote = internalNote;
		}

	}

	public static class ClosureForm {

		@DateTimeFormat(pattern = "yyyy-MM-dd")
		private LocalDate startDate;

		@DateTimeFormat(pattern = "yyyy-MM-dd")
		private LocalDate endDate;

		private String ownerReason;

		private String internalNote;

		public LocalDate getStartDate() {
			return this.startDate;
		}

		public void setStartDate(LocalDate startDate) {
			this.startDate = startDate;
		}

		public LocalDate getEndDate() {
			return this.endDate;
		}

		public void setEndDate(LocalDate endDate) {
			this.endDate = endDate;
		}

		public String getOwnerReason() {
			return this.ownerReason;
		}

		public void setOwnerReason(String ownerReason) {
			this.ownerReason = ownerReason;
		}

		public String getInternalNote() {
			return this.internalNote;
		}

		public void setInternalNote(String internalNote) {
			this.internalNote = internalNote;
		}

	}

}
