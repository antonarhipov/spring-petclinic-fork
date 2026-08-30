package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.request.OwnerDashboardService;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.RequestWorkflowService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;

@Controller
public class OwnerRequestController {

	private final CurrentOwnerAccount currentOwner;

	private final OwnerRepository owners;

	private final RequestWorkflowService workflow;

	private final OwnerDashboardService dashboard;

	private final AvailabilityRepository policies;

	private final OwnerSchedulingQueryService schedulingQueries;

	public OwnerRequestController(CurrentOwnerAccount currentOwner, OwnerRepository owners,
			RequestWorkflowService workflow, OwnerDashboardService dashboard, AvailabilityRepository policies,
			OwnerSchedulingQueryService schedulingQueries) {
		this.currentOwner = currentOwner;
		this.owners = owners;
		this.workflow = workflow;
		this.dashboard = dashboard;
		this.policies = policies;
		this.schedulingQueries = schedulingQueries;
	}

	@GetMapping("/owner/dashboard")
	public String dashboard(Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("dashboard", this.dashboard.load(account.getOwnerId()));
		model.addAttribute("appointments", this.schedulingQueries.appointments(account.getOwnerId()));
		return "scheduling/owner/dashboard";
	}

	@GetMapping("/owner/pets/{petId}/scheduling-requests/new")
	public String newRequest(@PathVariable Integer petId, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		OwnerRequestForm form = new OwnerRequestForm();
		form.setPetId(petId);
		model.addAttribute("form", form);
		model.addAttribute("owner", this.owners.findById(account.getOwnerId()).orElseThrow());
		model.addAttribute("urgentCareGuidance", this.policies.currentPolicy().getUrgentCareGuidance());
		model.addAttribute("upcomingAppointments", this.schedulingQueries.appointments(account.getOwnerId()));
		return "scheduling/owner/request-form";
	}

	@PostMapping("/owner/scheduling-requests")
	public String create(@Valid @ModelAttribute("form") OwnerRequestForm form, BindingResult result,
			Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		if (result.hasErrors()) {
			model.addAttribute("owner", this.owners.findById(account.getOwnerId()).orElseThrow());
			model.addAttribute("urgentCareGuidance", this.policies.currentPolicy().getUrgentCareGuidance());
			return "scheduling/owner/request-form";
		}
		SchedulingRequest request = this.workflow.createRequest(account.getOwnerId(), form.getPetId(),
				form.getSourceText());
		if (request.getState() == RequestState.STAFF_HANDLING) {
			return "redirect:/owner/scheduling-requests/" + request.getId() + "/status";
		}
		return "redirect:/owner/scheduling-requests/" + request.getId() + "/consent";
	}

	@GetMapping("/owner/scheduling-requests/{id}/consent")
	public String consent(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		model.addAttribute("request", request);
		model.addAttribute("consentCopyVersion", this.policies.currentPolicy().getConsentCopyVersion());
		return "scheduling/owner/consent";
	}

	@PostMapping("/owner/scheduling-requests/{id}/consent/interpret")
	public String interpret(@PathVariable Long id, @RequestParam Integer expectedVersion,
			@RequestParam(defaultValue = "false") boolean agree, Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		if (!agree) {
			return "redirect:/owner/scheduling-requests/" + id + "/consent";
		}
		var operationId = this.workflow.agreeToInterpret(id, account.getOwnerId(), account.getId(), expectedVersion);
		return "redirect:/owner/scheduling-requests/" + id + "/processing/" + operationId;
	}

	@PostMapping("/owner/scheduling-requests/{id}/consent/manual")
	public String decline(@PathVariable Long id, @RequestParam Integer expectedVersion, Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.declineInterpretation(id, account.getOwnerId(), account.getId(), expectedVersion);
		return "redirect:/owner/scheduling-requests/" + id + "/status";
	}

	@GetMapping("/owner/scheduling-requests/{id}/processing/{operationId}")
	public String processing(@PathVariable Long id, @PathVariable String operationId, Authentication authentication,
			Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("request", this.workflow.requireOwned(id, account.getOwnerId()));
		model.addAttribute("operationId", operationId);
		return "scheduling/owner/processing";
	}

	@GetMapping("/owner/scheduling-requests/{id}/status")
	public String status(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		SchedulingRequest request = this.workflow.requireOwned(id, account.getOwnerId());
		model.addAttribute("request", request);
		model.addAttribute("urgentCareGuidance", this.policies.currentPolicy().getUrgentCareGuidance());
		if (request.getState() == RequestState.STAFF_HANDLING) {
			return "scheduling/owner/staff-status";
		}
		return "scheduling/owner/processing";
	}

	@GetMapping("/owner/scheduling-requests/{id}/revise")
	public String reviseForm(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("request", this.workflow.requireOwned(id, account.getOwnerId()));
		return "scheduling/owner/request-form";
	}

	@PostMapping("/owner/scheduling-requests/{id}/revise")
	public String revise(@PathVariable Long id, @RequestParam Integer expectedVersion, @RequestParam String sourceText,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.workflow.reviseSourceText(id, account.getOwnerId(), expectedVersion, sourceText);
		return "redirect:/owner/scheduling-requests/" + id + "/consent";
	}

	@GetMapping("/owner/scheduling-requests/{id}/suggestion")
	public String ready(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("request", this.workflow.requireOwned(id, account.getOwnerId()));
		return "scheduling/owner/suggestion";
	}

}
