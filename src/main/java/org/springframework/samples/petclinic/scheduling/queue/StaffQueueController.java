package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.ContactAttemptForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.ClaimForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.ReassignForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.UnclaimForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.VersionedQueueForm;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/queue")
public class StaffQueueController {

	private final StaffQueueQueryService queryService;

	private final QueueAssignmentService assignmentService;

	private final QueueContactService contactService;

	private final AccountRepository accountRepository;

	public StaffQueueController(StaffQueueQueryService queryService, QueueAssignmentService assignmentService,
			QueueContactService contactService, AccountRepository accountRepository) {
		this.queryService = queryService;
		this.assignmentService = assignmentService;
		this.contactService = contactService;
		this.accountRepository = accountRepository;
	}

	@GetMapping
	public String listQueue(@RequestParam(name = "state", required = false) QueueState state,
			@RequestParam(name = "assigneeId", required = false) Long assigneeId,
			@RequestParam(name = "urgency", required = false) Urgency urgency,
			@RequestParam(name = "fallbackReason", required = false) String fallbackReason, Model model) {
		List<StaffQueueQueryService.QueueItemSummaryDto> items = this.queryService.getFilteredQueue(state, assigneeId,
				urgency, fallbackReason);
		List<Account> staffAccounts = this.accountRepository.findAll()
			.stream()
			.filter(a -> a.getRole() == Role.STAFF)
			.toList();

		model.addAttribute("queueItems", items);
		model.addAttribute("staffAccounts", staffAccounts);
		model.addAttribute("selectedState", state);
		model.addAttribute("selectedAssigneeId", assigneeId);
		model.addAttribute("selectedUrgency", urgency);
		model.addAttribute("selectedFallbackReason", fallbackReason);
		return "staff/queue/list";
	}

	@GetMapping("/{id}")
	public String viewQueueItem(@PathVariable("id") Long id, Authentication authentication, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		List<Account> staffAccounts = this.accountRepository.findAll()
			.stream()
			.filter(a -> a.getRole() == Role.STAFF)
			.toList();

		model.addAttribute("queueItem", detail);
		model.addAttribute("staffAccounts", staffAccounts);
		model.addAttribute("contactAttemptForm", withVersions(new ContactAttemptForm(), detail));
		model.addAttribute("claimForm", withVersions(new ClaimForm(), detail));
		model.addAttribute("reassignForm", withVersions(new ReassignForm(), detail));
		model.addAttribute("unclaimForm", withVersions(new UnclaimForm(), detail));
		model.addAttribute("currentActorId", extractActorId(authentication));
		return "staff/queue/detail";
	}

	@PostMapping("/{id}/claim")
	public String claimQueueItem(@PathVariable("id") Long id, @ModelAttribute("claimForm") ClaimForm form,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.assignmentService.claim(id, actorId, form.getExpectedRequestVersion(),
					form.getExpectedWorkflowRevision(), form.getExpectedQueueVersion());
			redirectAttributes.addFlashAttribute("successMessage", "Queue item claimed successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/{id}/unclaim")
	public String unclaimQueueItem(@PathVariable("id") Long id, @ModelAttribute("unclaimForm") UnclaimForm form,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.assignmentService.unclaim(id, actorId, form.getReason(), form.getExpectedRequestVersion(),
					form.getExpectedWorkflowRevision(), form.getExpectedQueueVersion());
			redirectAttributes.addFlashAttribute("successMessage", "Queue item unclaimed.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/{id}/reassign")
	public String reassignQueueItem(@PathVariable("id") Long id, @ModelAttribute("reassignForm") ReassignForm form,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			if (form.getAssigneeAccountId() == null) {
				throw new IllegalArgumentException("Please select a staff member to reassign to.");
			}
			this.assignmentService.reassign(id, form.getAssigneeAccountId(), actorId, form.getReason(),
					form.getExpectedRequestVersion(), form.getExpectedWorkflowRevision(),
					form.getExpectedQueueVersion());
			redirectAttributes.addFlashAttribute("successMessage", "Queue item reassigned successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/{id}/contact-attempts")
	public String recordContactAttempt(@PathVariable("id") Long id,
			@ModelAttribute("contactAttemptForm") ContactAttemptForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.contactService.recordContactAttempt(id, actorId, form.getOutcome(), form.getNote(),
					form.getExpectedRequestVersion(), form.getExpectedWorkflowRevision(),
					form.getExpectedQueueVersion());
			redirectAttributes.addFlashAttribute("successMessage", "Contact attempt recorded.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/queue/" + id;
	}

	private Long extractActorId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			return principal.getAccountId();
		}
		return 1L;
	}

	private <T extends VersionedQueueForm> T withVersions(T form, StaffQueueQueryService.QueueItemDetailDto detail) {
		form.setExpectedRequestVersion(detail.requestVersion());
		form.setExpectedWorkflowRevision(detail.workflowRevisionNumber());
		form.setExpectedQueueVersion(detail.queueVersion());
		return form;
	}

}
