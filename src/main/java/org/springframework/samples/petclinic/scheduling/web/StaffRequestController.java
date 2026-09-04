/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.web;

import java.util.List;

import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationWindow;
import org.springframework.samples.petclinic.scheduling.interpretation.StaffInterpretationService;
import org.springframework.samples.petclinic.scheduling.interpretation.WindowKind;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestEvent;
import org.springframework.samples.petclinic.scheduling.request.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.StaffSuggestionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for staff request detail, interpretation, solver suggestion, and hold
 * release (AC-55..56, AC-86, AC-89..91, AC-121).
 */
@Controller
public class StaffRequestController {

	private final StaffInterpretationService staffInterpretationService;

	private final StaffSuggestionService staffSuggestionService;

	private final StaffQueueService staffQueueService;

	private final StaffOperationsQueryService queryService;

	public StaffRequestController(StaffInterpretationService staffInterpretationService,
			StaffSuggestionService staffSuggestionService, StaffQueueService staffQueueService,
			StaffOperationsQueryService queryService) {
		this.staffInterpretationService = staffInterpretationService;
		this.staffSuggestionService = staffSuggestionService;
		this.staffQueueService = staffQueueService;
		this.queryService = queryService;
	}

	@GetMapping("/staff/requests/{requestId}")
	public String showRequestDetail(@PathVariable("requestId") Integer requestId, Model model) {
		SchedulingRequest request = this.queryService.getRequest(requestId);
		Interpretation interpretation = this.staffInterpretationService.getLatestInterpretation(requestId);
		List<SchedulingRequestEvent> timeline = this.staffInterpretationService.getTimeline(requestId);

		model.addAttribute("request", request);
		model.addAttribute("interpretation", interpretation);
		model.addAttribute("timeline", timeline);

		return "staff/requestDetail";
	}

	@GetMapping("/staff/requests/{requestId}/interpretation")
	public String showInterpretationForm(@PathVariable("requestId") Integer requestId, Model model) {
		SchedulingRequest request = this.queryService.getRequest(requestId);
		Interpretation interpretation = this.staffInterpretationService.getLatestInterpretation(requestId);

		StaffInterpretationForm form = new StaffInterpretationForm();
		if (interpretation != null) {
			form.setReasonSummary(interpretation.getReasonSummary());
			form.setEstimatedMinutes(interpretation.getEstimatedMinutes());
			form.setCareType(interpretation.getCareType() != null ? interpretation.getCareType().name() : "GENERAL");
			form.setSpecialty(interpretation.getSpecialty());
			if (interpretation.getPreferredVet() != null) {
				form.setPreferredVetId(interpretation.getPreferredVet().getId());
			}
			form.setCannotInterpret(interpretation.isCannotInterpret());

			StringBuilder preferred = new StringBuilder();
			StringBuilder allowed = new StringBuilder();
			StringBuilder excluded = new StringBuilder();

			for (InterpretationWindow w : interpretation.getWindows()) {
				String desc = (w.getTokens() != null && !w.getTokens().isBlank()) ? w.getTokens()
						: (w.getDayOfWeek() != null ? w.getDayOfWeek().name() : "")
								+ (w.getStartTime() != null ? ":" + w.getStartTime() + "-" + w.getEndTime() : "");
				if (w.getKind() == WindowKind.PREFERRED) {
					if (!preferred.isEmpty()) {
						preferred.append("; ");
					}
					preferred.append(desc);
				}
				else if (w.getKind() == WindowKind.ALLOWED) {
					if (!allowed.isEmpty()) {
						allowed.append("; ");
					}
					allowed.append(desc);
				}
				else if (w.getKind() == WindowKind.EXCLUDED) {
					if (!excluded.isEmpty()) {
						excluded.append("; ");
					}
					excluded.append(desc);
				}
			}
			form.setPreferredWindows(preferred.toString());
			form.setAllowedWindows(allowed.toString());
			form.setExcludedWindows(excluded.toString());
		}
		else {
			form.setReasonSummary(request.getReasonText());
			form.setEstimatedMinutes(30);
			form.setCareType("GENERAL");
			form.setPreferredWindows(request.getAvailabilityText());
		}

		model.addAttribute("request", request);
		model.addAttribute("form", form);
		model.addAttribute("vets", this.queryService.findVets());

		return "staff/interpretationForm";
	}

	@PostMapping("/staff/requests/{requestId}/interpretation")
	public String saveInterpretation(@PathVariable("requestId") Integer requestId,
			@ModelAttribute("form") StaffInterpretationForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		this.staffInterpretationService.saveInterpretation(requestId, form, "staff");
		redirectAttributes.addFlashAttribute("message", "interpretationSaved");
		return "redirect:/staff/requests/" + requestId;
	}

	@PostMapping("/staff/requests/{requestId}/suggest")
	public String runSolverSuggestion(@PathVariable("requestId") Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			this.staffSuggestionService.suggest(requestId, "staff");
			redirectAttributes.addFlashAttribute("message", "suggestionOffered");
		}
		catch (IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/requests/" + requestId;
	}

	@PostMapping("/staff/requests/{requestId}/release-hold")
	public String releaseHold(@PathVariable("requestId") Integer requestId,
			@RequestParam(name = "reason", required = false) String reason, RedirectAttributes redirectAttributes) {
		this.staffQueueService.releaseHold(requestId, "staff", reason);
		redirectAttributes.addFlashAttribute("message", "holdReleased");
		return "redirect:/staff/requests/" + requestId;
	}

}
