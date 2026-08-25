/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.owner;

import java.util.Collection;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.security.UserAccount;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

@Controller
@PreAuthorize("hasRole('OWNER')")
public class OwnerSelfServiceController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private static final String VIEWS_PETS_CREATE_OR_UPDATE_FORM = "pets/createOrUpdatePetForm";

	private final OwnerRepository owners;

	private final PetTypeRepository petTypes;

	private final UserAccountRepository userAccountRepository;

	public OwnerSelfServiceController(OwnerRepository owners, PetTypeRepository petTypes,
			UserAccountRepository userAccountRepository) {
		this.owners = owners;
		this.petTypes = petTypes;
		this.userAccountRepository = userAccountRepository;
	}

	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.petTypes.findPetTypes();
	}

	@InitBinder("pet")
	public void initPetBinder(WebDataBinder dataBinder) {
		dataBinder.setValidator(new PetValidator());
	}

	private Owner getAuthenticatedOwner(Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
		}
		UserAccount account = this.userAccountRepository.findByUsername(authentication.getName()).orElse(null);
		if (account == null || account.getOwner() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No owner profile linked to this account");
		}
		return account.getOwner();
	}

	@GetMapping("/my-profile")
	public ModelAndView showProfile(Authentication authentication) {
		Owner owner = getAuthenticatedOwner(authentication);
		ModelAndView mav = new ModelAndView("owners/ownerDetails");
		mav.addObject(owner);
		return mav;
	}

	@GetMapping("/my-profile/edit")
	public String initUpdateOwnerForm(Authentication authentication, ModelMap model) {
		Owner owner = getAuthenticatedOwner(authentication);
		model.addAttribute("owner", owner);
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/my-profile/edit")
	public String processUpdateOwnerForm(@Valid Owner updatedOwner, BindingResult result,
			Authentication authentication) {
		Owner currentOwner = getAuthenticatedOwner(authentication);
		if (result.hasErrors()) {
			return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
		}

		currentOwner.setFirstName(updatedOwner.getFirstName());
		currentOwner.setLastName(updatedOwner.getLastName());
		currentOwner.setAddress(updatedOwner.getAddress());
		currentOwner.setCity(updatedOwner.getCity());
		currentOwner.setTelephone(updatedOwner.getTelephone());
		this.owners.save(currentOwner);
		return "redirect:/my-profile";
	}

	@GetMapping("/my-pets/new")
	public String initCreationForm(Authentication authentication, ModelMap model) {
		Owner owner = getAuthenticatedOwner(authentication);
		Pet pet = new Pet();
		owner.addPet(pet);
		model.put("owner", owner);
		model.put("pet", pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/my-pets/new")
	public String processCreationForm(Authentication authentication, @Valid Pet pet, BindingResult result,
			ModelMap model) {
		Owner owner = getAuthenticatedOwner(authentication);
		if (pet.getName() != null && !pet.getName().isEmpty() && pet.isNew()
				&& owner.getPet(pet.getName(), true) != null) {
			result.rejectValue("name", "duplicate", "already exists");
		}

		if (result.hasErrors()) {
			model.put("owner", owner);
			model.put("pet", pet);
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		owner.addPet(pet);
		this.owners.save(owner);
		return "redirect:/my-profile";
	}

	@GetMapping("/my-pets/{petId}/edit")
	public String initUpdatePetForm(@PathVariable("petId") int petId, Authentication authentication, ModelMap model) {
		Owner owner = getAuthenticatedOwner(authentication);
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not own this pet");
		}
		model.put("owner", owner);
		model.put("pet", pet);
		return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/my-pets/{petId}/edit")
	public String processUpdatePetForm(@PathVariable("petId") int petId, @Valid Pet pet, BindingResult result,
			Authentication authentication, ModelMap model) {
		Owner owner = getAuthenticatedOwner(authentication);
		Pet existingPet = owner.getPet(petId);
		if (existingPet == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: you do not own this pet");
		}

		if (result.hasErrors()) {
			model.put("owner", owner);
			model.put("pet", pet);
			return VIEWS_PETS_CREATE_OR_UPDATE_FORM;
		}

		existingPet.setName(pet.getName());
		existingPet.setBirthDate(pet.getBirthDate());
		existingPet.setType(pet.getType());
		this.owners.save(owner);
		return "redirect:/my-profile";
	}

}
