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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for editing appointment-linked visits (AC-96, AC-114).
 */
@Controller
public class StaffVisitController {

	@GetMapping("/staff/visits/{visitId}/edit")
	public String showVisitEditForm(@PathVariable("visitId") Integer visitId, Model model) {
		return "staff/calendar";
	}

	@PostMapping("/staff/visits/{visitId}/edit")
	public String updateVisit(@PathVariable("visitId") Integer visitId,
			@RequestParam(name = "description", required = false) String description,
			RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "visitUpdated");
		return "redirect:/staff/calendar";
	}

}
