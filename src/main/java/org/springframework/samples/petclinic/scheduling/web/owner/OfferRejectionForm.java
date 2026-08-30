package org.springframework.samples.petclinic.scheduling.web.owner;

public class OfferRejectionForm {

	private Integer expectedVersion;

	private String reason;

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

}
