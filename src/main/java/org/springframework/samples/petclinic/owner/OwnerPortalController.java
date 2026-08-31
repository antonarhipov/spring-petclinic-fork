package org.springframework.samples.petclinic.owner;

import java.util.Objects;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/owner")
public class OwnerPortalController {

	private final OwnerAccessService ownerAccessService;

	public OwnerPortalController(OwnerAccessService ownerAccessService) {
		this.ownerAccessService = Objects.requireNonNull(ownerAccessService, "ownerAccessService must not be null");
	}

	@GetMapping("/profile")
	public String showProfile(@AuthenticationPrincipal PetClinicPrincipal principal, Model model) {
		if (principal == null || principal.getOwnerId() == null) {
			throw new AccessDeniedException("Owner account required");
		}
		Owner owner = this.ownerAccessService.getOwnerProfile(principal.getOwnerId(), principal);
		model.addAttribute("owner", owner);
		return "owner/profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@AuthenticationPrincipal PetClinicPrincipal principal,
			@RequestParam("address") String address, @RequestParam("city") String city,
			@RequestParam("telephone") String telephone, RedirectAttributes redirectAttributes) {
		if (principal == null || principal.getOwnerId() == null) {
			throw new AccessDeniedException("Owner account required");
		}
		try {
			this.ownerAccessService.updateOwnerContact(principal.getOwnerId(), address, city, telephone, principal);
			redirectAttributes.addFlashAttribute("message", "Contact details updated successfully.");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error", "Failed to update profile: " + e.getMessage());
		}
		return "redirect:/owner/profile";
	}

}
