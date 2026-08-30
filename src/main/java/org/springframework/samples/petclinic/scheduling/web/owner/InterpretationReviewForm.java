package org.springframework.samples.petclinic.scheduling.web.owner;

public class InterpretationReviewForm {

	private Integer expectedVersion;

	private String visitReason;

	private Integer durationMinutes;

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

}
