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
 * Controller for staff appointment lifecycle operations (AC-96, AC-109..113, AC-115,
 * RULE-16, RULE-36).
 */
@Controller
public class StaffAppointmentController {

	@GetMapping("/staff/appointments/{appointmentId}/reschedule")
	public String showRescheduleForm(@PathVariable("appointmentId") Integer appointmentId, Model model) {
		return "staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/reschedule")
	public String rescheduleAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "appointmentRescheduled");
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "appointmentCancelled");
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/complete")
	public String completeAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "appointmentCompleted");
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/no-show")
	public String noShowAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("message", "appointmentNoShow");
		return "redirect:/staff/calendar";
	}

}
