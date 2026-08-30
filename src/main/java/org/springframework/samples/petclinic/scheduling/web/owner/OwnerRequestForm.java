package org.springframework.samples.petclinic.scheduling.web.owner;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class OwnerRequestForm {

	@NotNull
	private Integer petId;

	@Size(min = 10, max = 2000)
	private String sourceText;

	public Integer getPetId() {
		return this.petId;
	}

	public void setPetId(Integer petId) {
		this.petId = petId;
	}

	public String getSourceText() {
		return this.sourceText;
	}

	public void setSourceText(String sourceText) {
		this.sourceText = sourceText;
	}

}
