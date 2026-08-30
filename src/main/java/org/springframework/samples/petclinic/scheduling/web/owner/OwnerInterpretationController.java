package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWindowRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class OwnerInterpretationController {

	private final CurrentOwnerAccount currentOwner;

	private final RequestWorkflowService workflow;

	private final RequestRevisionRepository revisions;

	private final RequestWindowRepository windows;

	public OwnerInterpretationController(CurrentOwnerAccount currentOwner, RequestWorkflowService workflow,
			RequestRevisionRepository revisions, RequestWindowRepository windows) {
		this.currentOwner = currentOwner;
		this.workflow = workflow;
		this.revisions = revisions;
		this.windows = windows;
	}

	@GetMapping("/owner/scheduling-requests/{id}/interpretation")
	public String review(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		RequestRevision revision = this.revisions.findById(request.getActiveRequestRevisionId()).orElseThrow();
		InterpretationReviewForm form = new InterpretationReviewForm();
		form.setExpectedVersion(request.getVersion());
		form.setVisitReason(revision.getVisitReason());
		form.setDurationMinutes(revision.getDurationMinutes());
		model.addAttribute("request", request);
		model.addAttribute("revision", revision);
		model.addAttribute("windows", this.windows.findByRequestRevisionId(revision.getId()));
		model.addAttribute("form", form);
		model.addAttribute("confirmEnabled", request.getState() == RequestState.INTERPRETATION_REVIEW);
		return "scheduling/owner/interpretation-review";
	}

	@PostMapping("/owner/scheduling-requests/{id}/interpretation")
	public String save(@PathVariable Long id, @ModelAttribute InterpretationReviewForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.saveOwnerEdits(id, account.getOwnerId(), form.getExpectedVersion(), form.getVisitReason(),
				form.getDurationMinutes());
		return "redirect:/owner/scheduling-requests/" + id + "/interpretation";
	}

	@PostMapping("/owner/scheduling-requests/{id}/interpretation/confirm")
	public String confirm(@PathVariable Long id, @ModelAttribute InterpretationReviewForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.confirm(id, account.getOwnerId(), account.getId(), form.getExpectedVersion());
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		if (request.getState() != RequestState.READY_FOR_SUGGESTION) {
			return "redirect:/owner/scheduling-requests/" + id + "/interpretation";
		}
		return "redirect:/owner/scheduling-requests/" + id + "/suggestion";
	}

}
