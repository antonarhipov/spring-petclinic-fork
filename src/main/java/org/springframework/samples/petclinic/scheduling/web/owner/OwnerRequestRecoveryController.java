package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.OwnerRequestHistoryService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResumeRouteResolver;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionService;
import org.springframework.samples.petclinic.scheduling.request.RequestWithdrawalService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OwnerRequestRecoveryController {

	private final CurrentOwnerAccount currentOwner;

	private final RequestWorkflowService workflow;

	private final RequestRevisionService revisions;

	private final RequestWithdrawalService withdrawals;

	private final OwnerRequestHistoryService history;

	private final OwnerResumeRouteResolver resumeRoutes;

	public OwnerRequestRecoveryController(CurrentOwnerAccount currentOwner, RequestWorkflowService workflow,
			RequestRevisionService revisions, RequestWithdrawalService withdrawals, OwnerRequestHistoryService history,
			OwnerResumeRouteResolver resumeRoutes) {
		this.currentOwner = currentOwner;
		this.workflow = workflow;
		this.revisions = revisions;
		this.withdrawals = withdrawals;
		this.history = history;
		this.resumeRoutes = resumeRoutes;
	}

	@GetMapping("/owner/scheduling-requests/{id}/resume")
	public String resume(@PathVariable Long id, Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		return "redirect:" + this.resumeRoutes.resumeUrl(request);
	}

	@GetMapping("/owner/scheduling-requests/{id}/history")
	public String history(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("history", this.history.load(id, account.getOwnerId()));
		return "scheduling/owner/request-history";
	}

	@GetMapping("/owner/scheduling-requests/{id}/withdraw")
	public String withdrawDialog(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("request", this.workflow.requireOwned(id, account.getOwnerId()));
		return "scheduling/owner/withdraw-dialog";
	}

	@PostMapping("/owner/scheduling-requests/{id}/withdraw")
	public String withdraw(@PathVariable Long id, @RequestParam Integer expectedVersion,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.withdrawals.withdraw(id, account.getOwnerId(), account.getId(), expectedVersion);
		return "redirect:/owner/dashboard";
	}

	@GetMapping("/owner/scheduling-requests/{id}/revise-request")
	public String reviseForm(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		OwnerRequestForm form = new OwnerRequestForm();
		form.setPetId(request.getPetId());
		model.addAttribute("form", form);
		model.addAttribute("request", request);
		return "scheduling/owner/revise-request";
	}

	@PostMapping("/owner/scheduling-requests/{id}/revise-request")
	public String revise(@PathVariable Long id, @RequestParam Integer expectedVersion, @RequestParam String sourceText,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.revisions.reviseSource(id, account.getOwnerId(), expectedVersion, sourceText);
		return "redirect:/owner/scheduling-requests/" + id + "/consent";
	}

}
