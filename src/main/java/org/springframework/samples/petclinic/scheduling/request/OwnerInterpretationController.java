package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner/requests/{requestId}/interpretation")
public class OwnerInterpretationController {

	private final OwnerInterpretationService interpretationService;

	public OwnerInterpretationController(OwnerInterpretationService interpretationService) {
		this.interpretationService = interpretationService;
	}

	@GetMapping
	public String reviewInterpretation(@PathVariable("requestId") Long requestId, Authentication authentication,
			Model model) {
		Integer ownerId = extractOwnerId(authentication);
		OwnerInterpretationService.InterpretationReviewDto review = this.interpretationService
			.getInterpretationForReview(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Interpretation details not found"));

		model.addAttribute("review", review);
		return "owner/requests/interpretation";
	}

	@PostMapping("/confirm")
	public String confirmInterpretation(@PathVariable("requestId") Long requestId, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		this.interpretationService.confirmInterpretation(requestId, ownerId);
		return "redirect:/owner/requests/" + requestId;
	}

	private Integer extractOwnerId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.getOwnerId() != null) {
				return principal.getOwnerId();
			}
		}
		throw new AccessDeniedException("Authenticated owner principal required");
	}

}
