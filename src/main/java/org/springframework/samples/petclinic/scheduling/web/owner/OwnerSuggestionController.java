package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.matching.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.matching.MatchingMode;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OwnerSuggestionController {

	private final CurrentOwnerAccount currentOwner;

	private final RequestWorkflowService workflow;

	private final MatchingCoordinator matching;

	public OwnerSuggestionController(CurrentOwnerAccount currentOwner, RequestWorkflowService workflow,
			MatchingCoordinator matching) {
		this.currentOwner = currentOwner;
		this.workflow = workflow;
		this.matching = matching;
	}

	@PostMapping("/owner/scheduling-requests/{id}/suggestions")
	public String requestSuggestion(@PathVariable Long id, @RequestParam Integer expectedVersion,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		var operationId = this.matching.requestSuggestion(id, account.getOwnerId(), expectedVersion,
				MatchingMode.PREFERRED_ONLY);
		return "redirect:/owner/scheduling-requests/" + id + "/processing/" + operationId;
	}

	@GetMapping("/owner/scheduling-requests/{id}/fallback-choice")
	public String fallbackChoice(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("request", this.workflow.requireOwned(id, account.getOwnerId()));
		return "scheduling/owner/fallback-choice";
	}

	@PostMapping("/owner/scheduling-requests/{id}/suggestions/alternative")
	public String alternative(@PathVariable Long id, @RequestParam Integer expectedVersion,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		var operationId = this.matching.requestSuggestion(id, account.getOwnerId(), expectedVersion,
				MatchingMode.ALLOWED_FALLBACK);
		return "redirect:/owner/scheduling-requests/" + id + "/processing/" + operationId;
	}

	@PostMapping("/owner/scheduling-requests/{id}/forward-to-staff")
	public String forward(@PathVariable Long id, @RequestParam Integer expectedVersion, Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.matching.forwardToStaff(id, account.getOwnerId(), expectedVersion);
		return "redirect:/owner/scheduling-requests/" + id + "/status";
	}

}
