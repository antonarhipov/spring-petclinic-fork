package org.springframework.samples.petclinic.owner;

import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@Controller
public class OwnerSelfServiceController {

	private final OwnerRepository ownerRepository;

	private final PetRepository petRepository;

	private final PetTypeRepository petTypeRepository;

	public OwnerSelfServiceController(OwnerRepository ownerRepository, PetRepository petRepository,
			PetTypeRepository petTypeRepository) {
		this.ownerRepository = ownerRepository;
		this.petRepository = petRepository;
		this.petTypeRepository = petTypeRepository;
	}

	@ModelAttribute("types")
	public Collection<PetType> populatePetTypes() {
		return this.petTypeRepository.findPetTypes();
	}

	@GetMapping("/my-profile")
	public String showMyProfile(@AuthenticationPrincipal UserPrincipal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		List<Pet> pets = petRepository.findByOwnerId(owner.getId());
		model.addAttribute("owner", owner);
		model.addAttribute("pets", pets);
		return "owner/myProfile";
	}

	@GetMapping("/my-profile/edit")
	public String initUpdateProfileForm(@AuthenticationPrincipal UserPrincipal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		model.addAttribute("ownerProfileForm", OwnerProfileForm.fromOwner(owner));
		return "owner/editProfile";
	}

	@PostMapping("/my-profile/edit")
	public String processUpdateProfileForm(@Valid @ModelAttribute("ownerProfileForm") OwnerProfileForm form,
			BindingResult result, @AuthenticationPrincipal UserPrincipal principal) {
		if (result.hasErrors()) {
			return "owner/editProfile";
		}
		Owner owner = getAuthenticatedOwner(principal);
		form.updateOwner(owner);
		ownerRepository.save(owner);
		return "redirect:/my-profile";
	}

	@GetMapping("/my-pets/new")
	public String initCreationForm(@AuthenticationPrincipal UserPrincipal principal, Model model) {
		getAuthenticatedOwner(principal);
		PetSelfServiceForm form = new PetSelfServiceForm();
		model.addAttribute("petForm", form);
		return "owner/myPetForm";
	}

	@PostMapping("/my-pets/new")
	public String processCreationForm(@Valid @ModelAttribute("petForm") PetSelfServiceForm form, BindingResult result,
			@AuthenticationPrincipal UserPrincipal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		if (result.hasErrors()) {
			return "owner/myPetForm";
		}

		PetType type = petTypeRepository.findById(form.getTypeId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet type"));

		Pet pet = new Pet();
		pet.setName(form.getName());
		pet.setBirthDate(form.getBirthDate());
		pet.setType(type);
		owner.addPet(pet);
		ownerRepository.save(owner);

		return "redirect:/my-profile";
	}

	@GetMapping("/my-pets/{petId}/edit")
	public String initUpdatePetForm(@PathVariable("petId") Integer petId,
			@AuthenticationPrincipal UserPrincipal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		Pet pet = petRepository.findByIdAndOwnerId(petId, owner.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));

		model.addAttribute("petForm", PetSelfServiceForm.fromPet(pet));
		return "owner/myPetForm";
	}

	@PostMapping("/my-pets/{petId}/edit")
	public String processUpdatePetForm(@PathVariable("petId") Integer petId,
			@Valid @ModelAttribute("petForm") PetSelfServiceForm form, BindingResult result,
			@AuthenticationPrincipal UserPrincipal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		Pet pet = petRepository.findByIdAndOwnerId(petId, owner.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));

		if (result.hasErrors()) {
			return "owner/myPetForm";
		}

		PetType type = petTypeRepository.findById(form.getTypeId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pet type"));

		pet.setName(form.getName());
		pet.setBirthDate(form.getBirthDate());
		pet.setType(type);
		petRepository.save(pet);

		return "redirect:/my-profile";
	}

	private Owner getAuthenticatedOwner(UserPrincipal principal) {
		if (principal == null || principal.getOwnerId() == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found");
		}
		return ownerRepository.findById(principal.getOwnerId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
	}

}
