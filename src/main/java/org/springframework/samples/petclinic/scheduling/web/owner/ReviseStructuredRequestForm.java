package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.web.owner.InterpretationReviewForm.WindowRow;

public class ReviseStructuredRequestForm {

	private Integer expectedVersion;

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

}
