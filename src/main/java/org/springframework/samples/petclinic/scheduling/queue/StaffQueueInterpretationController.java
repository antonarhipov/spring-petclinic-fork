package org.springframework.samples.petclinic.scheduling.queue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.EmergencyClearanceForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.ManualInterpretationForm;
import org.springframework.samples.petclinic.scheduling.queue.StaffInterpretationService.ManualInterpretationCommand;
import org.springframework.samples.petclinic.scheduling.request.AvailabilityWindow;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/queue")
public class StaffQueueInterpretationController {

	private final StaffQueueQueryService queryService;

	private final StaffInterpretationService interpretationService;

	private final EmergencyClearanceService emergencyClearanceService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final VetRepository vetRepository;

	public StaffQueueInterpretationController(StaffQueueQueryService queryService,
			StaffInterpretationService interpretationService, EmergencyClearanceService emergencyClearanceService,
			EffectiveAvailabilityService effectiveAvailabilityService, VetRepository vetRepository) {
		this.queryService = queryService;
		this.interpretationService = interpretationService;
		this.emergencyClearanceService = emergencyClearanceService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.vetRepository = vetRepository;
	}

	@GetMapping("/{id}/interpretation")
	public String interpretationForm(@PathVariable("id") Long id, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		ManualInterpretationForm form = new ManualInterpretationForm();
		form.setVisitReason(detail.visitReason());
		form.setDurationMinutes(detail.durationMinutes() != null ? detail.durationMinutes() : 30);
		form.setPreferredVetId(detail.preferredVetId());
		form.setRequiredSpecialtyId(detail.requiredSpecialtyId());
		form.setUrgency(detail.urgency());
		form.setExpectedRequestVersion(detail.requestVersion());
		form.setExpectedWorkflowRevision(detail.workflowRevisionNumber());
		form.setExpectedQueueVersion(detail.queueVersion());

		populateReferenceData(model);
		model.addAttribute("queueItem", detail);
		model.addAttribute("interpretationForm", form);
		return "staff/queue/interpretation";
	}

	@PostMapping("/{id}/interpretation")
	public String submitInterpretation(@PathVariable("id") Long id,
			@ModelAttribute("interpretationForm") ManualInterpretationForm form, Authentication authentication,
			RedirectAttributes redirectAttributes, Model model) {
		Long actorId = extractActorId(authentication);

		List<AvailabilityWindow> windows = new ArrayList<>();
		if (form.getStartDate() != null) {
			LocalDate endDate = form.getEndDate() != null ? form.getEndDate() : form.getStartDate();
			LocalTime startTime = form.getStartTime() != null ? form.getStartTime() : LocalTime.of(9, 0);
			LocalTime endTime = form.getEndTime() != null ? form.getEndTime() : LocalTime.of(17, 0);

			AvailabilityWindow window = new AvailabilityWindow(null, WindowClassification.ALLOWED, WindowShape.ONE_OFF,
					form.getStartDate(), form.getStartDate(), endDate, null, startTime, endTime, "Staff manual window",
					null);
			windows.add(window);
		}

		ManualInterpretationCommand cmd = new ManualInterpretationCommand(id, actorId, form.getVisitReason(),
				form.getDurationMinutes(), form.getPreferredVetId(), form.getRequiredSpecialtyId(), form.getUrgency(),
				windows, form.isRequestOwnerConfirmation(), form.getExpectedRequestVersion(),
				form.getExpectedWorkflowRevision(), form.getExpectedQueueVersion());

		try {
			this.interpretationService.recordManualInterpretation(cmd);
			redirectAttributes.addFlashAttribute("successMessage", "Interpretation saved successfully.");
			return "redirect:/staff/queue/" + id;
		}
		catch (Exception ex) {
			populateReferenceData(model);
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/queue/interpretation";
		}
	}

	@GetMapping("/{id}/emergency-clearance")
	public String emergencyClearanceForm(@PathVariable("id") Long id, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		model.addAttribute("queueItem", detail);
		EmergencyClearanceForm form = new EmergencyClearanceForm();
		form.setExpectedRequestVersion(detail.requestVersion());
		form.setExpectedWorkflowRevision(detail.workflowRevisionNumber());
		form.setExpectedQueueVersion(detail.queueVersion());
		model.addAttribute("emergencyClearanceForm", form);
		return "staff/queue/emergency-clearance";
	}

	@PostMapping("/{id}/emergency-clearance")
	public String submitEmergencyClearance(@PathVariable("id") Long id,
			@ModelAttribute("emergencyClearanceForm") EmergencyClearanceForm form, Authentication authentication,
			RedirectAttributes redirectAttributes, Model model) {
		Long actorId = extractActorId(authentication);

		if (form.getClinicalJustification() == null || form.getClinicalJustification().isBlank()) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", "Clinical justification is required.");
			return "staff/queue/emergency-clearance";
		}

		try {
			this.emergencyClearanceService.clearEmergency(id, actorId, form.getNewUrgency(),
					form.getClinicalJustification(), form.getExpectedRequestVersion(),
					form.getExpectedWorkflowRevision(), form.getExpectedQueueVersion());
			redirectAttributes.addFlashAttribute("successMessage",
					"Emergency flag cleared. Reconfirmation requested from owner.");
			return "redirect:/staff/queue/" + id;
		}
		catch (Exception ex) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/queue/emergency-clearance";
		}
	}

	private void populateReferenceData(Model model) {
		model.addAttribute("allVets", this.vetRepository.findAll());
		model.addAttribute("allSpecialties", this.vetRepository.findSpecialties());
		model.addAttribute("allowedDurations",
				this.effectiveAvailabilityService.getClinicPolicy().getAllowedDurations());
	}

	private Long extractActorId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			return principal.getAccountId();
		}
		return 1L;
	}

}
