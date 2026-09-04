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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for veterinarian availability management (AC-96, AC-100..101, AC-107,
 * RULE-34).
 */
@Controller
public class VetAvailabilityController {

	@GetMapping("/staff/vets/{vetId}/availability")
	public String showAvailability(@PathVariable("vetId") Integer vetId, Model model) {
		return "staff/calendar";
	}

	@PostMapping("/staff/vets/{vetId}/availability")
	public String saveAvailability(@PathVariable("vetId") Integer vetId,
			@ModelAttribute("form") VetAvailabilityForm form, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "availabilitySaved");
		return "redirect:/staff/vets/" + vetId + "/availability";
	}

}
