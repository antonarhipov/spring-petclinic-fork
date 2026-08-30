package org.springframework.samples.petclinic.scheduling.web.owner;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class InterpretationReviewForm {

	private Integer expectedVersion;

	private String visitReason;

	private Integer durationMinutes;

	private Integer preferredVeterinarianId;

	private List<WindowRow> allowedWindows = newRows();

	private List<WindowRow> preferredWindows = newRows();

	private List<WindowRow> excludedWindows = newRows();

	private static List<WindowRow> newRows() {
		List<WindowRow> rows = new ArrayList<>();
		rows.add(new WindowRow());
		rows.add(new WindowRow());
		return rows;
	}

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

	public String getVisitReason() {
		return this.visitReason;
	}

	public void setVisitReason(String visitReason) {
		this.visitReason = visitReason;
	}

	public Integer getDurationMinutes() {
		return this.durationMinutes;
	}

	public void setDurationMinutes(Integer durationMinutes) {
		this.durationMinutes = durationMinutes;
	}

	public Integer getPreferredVeterinarianId() {
		return this.preferredVeterinarianId;
	}

	public void setPreferredVeterinarianId(Integer preferredVeterinarianId) {
		this.preferredVeterinarianId = preferredVeterinarianId;
	}

	public List<WindowRow> getAllowedWindows() {
		return this.allowedWindows;
	}

	public void setAllowedWindows(List<WindowRow> allowedWindows) {
		this.allowedWindows = allowedWindows;
	}

	public List<WindowRow> getPreferredWindows() {
		return this.preferredWindows;
	}

	public void setPreferredWindows(List<WindowRow> preferredWindows) {
		this.preferredWindows = preferredWindows;
	}

	public List<WindowRow> getExcludedWindows() {
		return this.excludedWindows;
	}

	public void setExcludedWindows(List<WindowRow> excludedWindows) {
		this.excludedWindows = excludedWindows;
	}

	public static class WindowRow {

		private LocalDate startDate;

		private LocalTime startTime;

		private LocalDate endDate;

		private LocalTime endTime;

		public LocalDate getStartDate() {
			return this.startDate;
		}

		public void setStartDate(LocalDate startDate) {
			this.startDate = startDate;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public LocalDate getEndDate() {
			return this.endDate;
		}

		public void setEndDate(LocalDate endDate) {
			this.endDate = endDate;
		}

		public LocalTime getEndTime() {
			return this.endTime;
		}

		public void setEndTime(LocalTime endTime) {
			this.endTime = endTime;
		}

	}

}
