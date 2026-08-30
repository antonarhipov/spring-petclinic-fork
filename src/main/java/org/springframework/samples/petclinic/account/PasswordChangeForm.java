package org.springframework.samples.petclinic.account;

public class PasswordChangeForm {

	private String currentPassword;

	private String newPassword;

	private String confirmPassword;

	public String getCurrentPassword() {
		return this.currentPassword;
	}

	public void setCurrentPassword(String currentPassword) {
		this.currentPassword = currentPassword;
	}

	public String getNewPassword() {
		return this.newPassword;
	}

	public void setNewPassword(String newPassword) {
		this.newPassword = newPassword;
	}

	public String getConfirmPassword() {
		return this.confirmPassword;
	}

	public void setConfirmPassword(String confirmPassword) {
		this.confirmPassword = confirmPassword;
	}

	public void clearSecrets() {
		this.currentPassword = null;
		this.newPassword = null;
		this.confirmPassword = null;
	}

}
