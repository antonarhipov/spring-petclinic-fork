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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for staff direct appointment booking (AC-92..94).
 */
@Controller
public class StaffBookingController {

	@GetMapping("/staff/appointments/new")
	public String showBookingForm(@RequestParam(name = "requestId", required = false) Integer requestId,
			@RequestParam(name = "petId", required = false) Integer petId, Model model) {
		StaffBookingForm form = new StaffBookingForm();
		if (requestId != null) {
			form.setRequestId(requestId);
		}
		if (petId != null) {
			form.setPetId(petId);
		}
		model.addAttribute("form", form);
		return "staff/queue";
	}

	@PostMapping("/staff/appointments")
	public String createBooking(@ModelAttribute("form") StaffBookingForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		return "redirect:/staff/calendar";
	}

}
