package org.springframework.samples.petclinic.owner;

import java.util.Objects;
import jakarta.validation.Valid;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
		model.addAttribute("form", OwnerProfileForm.from(owner));
		return "owner/profile";
	}

	@PostMapping("/profile")
	public String updateProfile(@AuthenticationPrincipal PetClinicPrincipal principal,
			@Valid @ModelAttribute("form") OwnerProfileForm form, BindingResult bindingResult, Model model,
			RedirectAttributes redirectAttributes) {
		if (principal == null || principal.getOwnerId() == null) {
			throw new AccessDeniedException("Owner account required");
		}
		if (bindingResult.hasErrors()) {
			model.addAttribute("owner", this.ownerAccessService.getOwnerProfile(principal.getOwnerId(), principal));
			return "owner/profile";
		}
		try {
			this.ownerAccessService.updateOwnerProfile(principal.getOwnerId(), form, principal);
			redirectAttributes.addFlashAttribute("message", "Profile updated successfully.");
		}
		catch (Exception e) {
			redirectAttributes.addFlashAttribute("error",
					"We could not update your profile. Review the fields and try again.");
		}
		return "redirect:/owner/profile";
	}

}
