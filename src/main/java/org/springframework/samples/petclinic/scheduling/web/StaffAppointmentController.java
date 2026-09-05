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

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentManagementService;
import org.springframework.samples.petclinic.vet.Vet;
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

	private final AppointmentManagementService appointmentManagementService;

	private final Clock clock;

	public StaffAppointmentController(AppointmentManagementService appointmentManagementService, Clock clock) {
		this.appointmentManagementService = appointmentManagementService;
		this.clock = clock;
	}

	@GetMapping("/staff/appointments/{appointmentId}/reschedule")
	public String showRescheduleForm(@PathVariable("appointmentId") Integer appointmentId, Model model) {
		Appointment appointment;
		try {
			appointment = this.appointmentManagementService.requireAppointment(appointmentId);
		}
		catch (IllegalArgumentException ex) {
			return "redirect:/staff/calendar";
		}
		AppointmentActionForm form = new AppointmentActionForm();
		form.setVetId(appointment.getVet().getId());
		form.setAppointmentDate(appointment.getStartTime().toLocalDate().toString());
		form.setStartTime(appointment.getStartTime().toLocalTime().toString());
		form.setDurationMinutes(appointment.getDuration());
		model.addAttribute("appointment", appointment);
		model.addAttribute("form", form);
		model.addAttribute("vets", this.appointmentManagementService.allVets());
		model.addAttribute("changes", this.appointmentManagementService.changes(appointmentId));
		return "staff/appointmentReschedule";
	}

	@PostMapping("/staff/appointments/{appointmentId}/reschedule")
	public String rescheduleAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			Appointment appointment = this.appointmentManagementService.requireAppointment(appointmentId);
			LocalDate date = LocalDate.parse(form.getAppointmentDate());
			LocalTime time = LocalTime.parse(form.getStartTime());
			ZonedDateTime newStartTime = date.atTime(time).atZone(this.clock.getZone());
			Vet vet = this.appointmentManagementService.requireVet(form.getVetId());
			this.appointmentManagementService.staffReschedule(appointment, actor(principal), form.getReason(),
					newStartTime, form.getDurationMinutes(), vet);
			redirectAttributes.addFlashAttribute("message", "appointmentRescheduled");
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", errorKey(form.getReason()));
		}
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@ModelAttribute("form") AppointmentActionForm form, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			Appointment appointment = this.appointmentManagementService.requireAppointment(appointmentId);
			this.appointmentManagementService.staffCancel(appointment, actor(principal), form.getReason());
			redirectAttributes.addFlashAttribute("message", "appointmentCancelled");
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", errorKey(form.getReason()));
		}
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

	private String actor(Principal principal) {
		return principal == null || principal.getName() == null || principal.getName().isBlank() ? "staff"
				: principal.getName();
	}

	private String errorKey(String reason) {
		return reason == null || reason.isBlank() ? "reasonRequired" : "appointmentActionNotAllowed";
	}

}
