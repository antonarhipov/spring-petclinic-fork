package org.springframework.samples.petclinic.owner;

import java.util.List;

import org.springframework.samples.petclinic.audit.OwnerHistoryQueryService;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner/history")
public class OwnerHistoryController {

	private final OwnerHistoryQueryService historyQueryService;

	public OwnerHistoryController(OwnerHistoryQueryService historyQueryService) {
		this.historyQueryService = historyQueryService;
	}

	@GetMapping
	public String viewHistory(Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		List<OwnerHistoryQueryService.OwnerHistoryItemDto> events = this.historyQueryService
			.getHistoryForOwner(ownerId);
		model.addAttribute("events", events);
		return "owner/history";
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
