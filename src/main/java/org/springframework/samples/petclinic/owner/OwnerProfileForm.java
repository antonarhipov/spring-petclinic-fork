package org.springframework.samples.petclinic.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class OwnerProfileForm {

	@NotBlank
	@Size(max = 30)
	private String firstName;

	@NotBlank
	@Size(max = 30)
	private String lastName;

	@NotBlank
	@Size(max = 255)
	private String address;

	@NotBlank
	@Size(max = 80)
	private String city;

	@NotBlank
	@Pattern(regexp = "\\d{10}", message = "{telephone.invalid}")
	private String telephone;

	public static OwnerProfileForm from(Owner owner) {
		OwnerProfileForm form = new OwnerProfileForm();
		form.setFirstName(owner.getFirstName());
		form.setLastName(owner.getLastName());
		form.setAddress(owner.getAddress());
		form.setCity(owner.getCity());
		form.setTelephone(owner.getTelephone());
		return form;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getAddress() {
		return this.address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return this.city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getTelephone() {
		return this.telephone;
	}

	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}

}
