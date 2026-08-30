package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.List;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.OwnerRequestHistoryService;
import org.springframework.samples.petclinic.scheduling.request.OwnerResumeRouteResolver;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionService;
import org.springframework.samples.petclinic.scheduling.request.RequestWithdrawalService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService.WindowEdit;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.web.owner.InterpretationReviewForm.WindowRow;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OwnerRequestRecoveryController {

	private final CurrentOwnerAccount currentOwner;

	private final RequestWorkflowService workflow;

	private final RequestRevisionService revisions;

	private final RequestRevisionRepository revisionRepository;

	private final RequestWithdrawalService withdrawals;

	private final OwnerRequestHistoryService history;

	private final OwnerResumeRouteResolver resumeRoutes;

	public OwnerRequestRecoveryController(CurrentOwnerAccount currentOwner, RequestWorkflowService workflow,
			RequestRevisionService revisions, RequestRevisionRepository revisionRepository,
			RequestWithdrawalService withdrawals, OwnerRequestHistoryService history,
			OwnerResumeRouteResolver resumeRoutes) {
		this.currentOwner = currentOwner;
		this.workflow = workflow;
		this.revisions = revisions;
		this.revisionRepository = revisionRepository;
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
		ReviseStructuredRequestForm detailsForm = new ReviseStructuredRequestForm();
		detailsForm.setExpectedVersion(request.getVersion());
		if (request.getActiveRequestRevisionId() != null) {
			this.revisionRepository.findById(request.getActiveRequestRevisionId()).ifPresent(revision -> {
				detailsForm.setDurationMinutes(revision.getDurationMinutes());
				detailsForm.setPreferredVeterinarianId(revision.getPreferredVeterinarianId());
			});
		}
		model.addAttribute("form", form);
		model.addAttribute("detailsForm", detailsForm);
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

	@PostMapping("/owner/scheduling-requests/{id}/revise-request/details")
	public String reviseDetails(@PathVariable Long id, @ModelAttribute ReviseStructuredRequestForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.revisions.reviseConfirmed(id, account.getOwnerId(), account.getId(), form.getExpectedVersion(),
				form.getDurationMinutes(), form.getPreferredVeterinarianId(), toEdits(form.getAllowedWindows()),
				toEdits(form.getPreferredWindows()), toEdits(form.getExcludedWindows()));
		return "redirect:/owner/scheduling-requests/" + id + "/suggestion";
	}

	private static List<WindowEdit> toEdits(List<WindowRow> rows) {
		return rows.stream()
			.map(row -> new WindowEdit(row.getStartDate(), row.getStartTime(), row.getEndDate(), row.getEndTime()))
			.toList();
	}

}
