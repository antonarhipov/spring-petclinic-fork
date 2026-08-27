package org.springframework.samples.petclinic.owner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OwnerProfileForm {

	@NotBlank
	private String firstName;

	@NotBlank
	private String lastName;

	@NotBlank
	private String address;

	@NotBlank
	private String city;

	@NotBlank
	@Pattern(regexp = "\\d{10}", message = "{telephone.invalid}")
	private String telephone;

	public OwnerProfileForm() {
	}

	public static OwnerProfileForm fromOwner(Owner owner) {
		OwnerProfileForm form = new OwnerProfileForm();
		form.setFirstName(owner.getFirstName());
		form.setLastName(owner.getLastName());
		form.setAddress(owner.getAddress());
		form.setCity(owner.getCity());
		form.setTelephone(owner.getTelephone());
		return form;
	}

	public void updateOwner(Owner owner) {
		owner.setFirstName(this.firstName);
		owner.setLastName(this.lastName);
		owner.setAddress(this.address);
		owner.setCity(this.city);
		owner.setTelephone(this.telephone);
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public String getCity() {
		return city;
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getTelephone() {
		return telephone;
	}

	public void setTelephone(String telephone) {
		this.telephone = telephone;
	}

}
