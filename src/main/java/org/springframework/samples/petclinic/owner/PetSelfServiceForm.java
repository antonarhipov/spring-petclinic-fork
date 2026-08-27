package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PetSelfServiceForm {

	private Integer id;

	@NotBlank
	private String name;

	@DateTimeFormat(pattern = "yyyy-MM-dd")
	private LocalDate birthDate;

	@NotNull
	private Integer typeId;

	public PetSelfServiceForm() {
	}

	public static PetSelfServiceForm fromPet(Pet pet) {
		PetSelfServiceForm form = new PetSelfServiceForm();
		form.setId(pet.getId());
		form.setName(pet.getName());
		form.setBirthDate(pet.getBirthDate());
		if (pet.getType() != null) {
			form.setTypeId(pet.getType().getId());
		}
		return form;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(LocalDate birthDate) {
		this.birthDate = birthDate;
	}

	public Integer getTypeId() {
		return typeId;
	}

	public void setTypeId(Integer typeId) {
		this.typeId = typeId;
	}

}
