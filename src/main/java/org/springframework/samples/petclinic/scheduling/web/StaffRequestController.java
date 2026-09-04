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

	@GetMapping("/staff/requests/{requestId}")
	public String showRequestDetail(@PathVariable("requestId") Integer requestId, Model model) {
		return "staff/queue";
	}

	@GetMapping("/staff/requests/{requestId}/interpretation")
	public String showInterpretationForm(@PathVariable("requestId") Integer requestId, Model model) {
		model.addAttribute("form", new StaffInterpretationForm());
		return "staff/queue";
	}

	@PostMapping("/staff/requests/{requestId}/interpretation")
	public String saveInterpretation(@PathVariable("requestId") Integer requestId,
			@ModelAttribute("form") StaffInterpretationForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		return "redirect:/staff/requests/" + requestId;
	}

	@PostMapping("/staff/requests/{requestId}/suggest")
	public String runSolverSuggestion(@PathVariable("requestId") Integer requestId,
			RedirectAttributes redirectAttributes) {
		return "redirect:/staff/requests/" + requestId;
	}

	@PostMapping("/staff/requests/{requestId}/release-hold")
	public String releaseHold(@PathVariable("requestId") Integer requestId,
			@RequestParam(name = "reason", required = false) String reason, RedirectAttributes redirectAttributes) {
		return "redirect:/staff/requests/" + requestId;
	}

}
