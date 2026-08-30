package org.springframework.samples.petclinic.scheduling.web.staff;

public class QueueAssignmentForm {

	private Integer expectedVersion;

	private String reason;

	private Long assigneeId;

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public Long getAssigneeId() {
		return this.assigneeId;
	}

	public void setAssigneeId(Long assigneeId) {
		this.assigneeId = assigneeId;
	}

}
