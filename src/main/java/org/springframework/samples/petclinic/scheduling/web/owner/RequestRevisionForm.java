package org.springframework.samples.petclinic.scheduling.web.owner;

public class RequestRevisionForm {

	private Integer expectedVersion;

	private String sourceText;

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

	public String getSourceText() {
		return this.sourceText;
	}

	public void setSourceText(String sourceText) {
		this.sourceText = sourceText;
	}

}
