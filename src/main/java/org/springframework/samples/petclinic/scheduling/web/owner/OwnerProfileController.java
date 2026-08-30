package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.OwnerResourceNotFoundException;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class OwnerProfileController {

	private final CurrentOwnerAccount currentOwner;

	private final OwnerSchedulingQueryService queries;

	public OwnerProfileController(CurrentOwnerAccount currentOwner, OwnerSchedulingQueryService queries) {
		this.currentOwner = currentOwner;
		this.queries = queries;
	}

	@GetMapping("/owner/profile")
	public String profile(Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("owner", this.queries.profile(account.getOwnerId()));
		return "scheduling/owner/profile";
	}

	@GetMapping("/owner/pets/{petId}")
	public String pet(@PathVariable Integer petId, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		Owner owner = this.queries.profile(account.getOwnerId());
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new OwnerResourceNotFoundException();
		}
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		return "scheduling/owner/profile";
	}

}
