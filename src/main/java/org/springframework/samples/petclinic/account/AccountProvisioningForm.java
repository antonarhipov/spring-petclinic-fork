package org.springframework.samples.petclinic.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AccountProvisioningForm {

	@NotBlank
	@Size(max = 80)
	private String username;

	private Integer expectedVersion = 0;

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public Integer getExpectedVersion() {
		return this.expectedVersion;
	}

	public void setExpectedVersion(Integer expectedVersion) {
		this.expectedVersion = expectedVersion;
	}

}
