package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OwnerHistoryController {

	private final CurrentOwnerAccount currentOwner;

	private final OwnerSchedulingQueryService queries;

	public OwnerHistoryController(CurrentOwnerAccount currentOwner, OwnerSchedulingQueryService queries) {
		this.currentOwner = currentOwner;
		this.queries = queries;
	}

	@GetMapping("/owner/history")
	public String history(Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("history", this.queries.history(account.getOwnerId()));
		return "scheduling/owner/history";
	}

}
