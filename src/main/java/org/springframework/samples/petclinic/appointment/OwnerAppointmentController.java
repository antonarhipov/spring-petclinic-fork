/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.scheduling.solver.SuggestionResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerAppointmentController {

	private final OwnerAppointmentService appointmentService;

	private final OwnerSchedulingResumeService resumeService;

	private final ClinicSettingsRepository settingsRepository;

	public OwnerAppointmentController(OwnerAppointmentService appointmentService,
			OwnerSchedulingResumeService resumeService, ClinicSettingsRepository settingsRepository) {
		this.appointmentService = appointmentService;
		this.resumeService = resumeService;
		this.settingsRepository = settingsRepository;
	}

	@GetMapping("/owners/{ownerId}/appointments")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String showAppointments(@PathVariable Integer ownerId, Model model) {
		model.addAttribute("ownerId", ownerId);
		model.addAttribute("appointments", this.appointmentService.getUpcoming(ownerId));
		model.addAttribute("requests", this.appointmentService.getActiveRequests(ownerId));
		model.addAttribute("clinicZone", this.settingsRepository.getClinicSettings().getZone());
		return "owners/appointments";
	}

	@PostMapping("/owners/{ownerId}/appointments/{appointmentId}/cancel")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String cancel(@PathVariable Integer ownerId, @PathVariable Integer appointmentId,
			RedirectAttributes redirectAttributes) {
		try {
			this.appointmentService.cancel(ownerId, appointmentId);
			redirectAttributes.addFlashAttribute("message", "Appointment cancelled");
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/owners/" + ownerId + "/appointments";
	}

	@PostMapping("/owners/{ownerId}/appointment-requests/{requestId}/resume")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String resume(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			SuggestionResult result = this.resumeService.resume(ownerId, requestId);
			redirectAttributes.addFlashAttribute("message", result.message());
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/owners/" + ownerId + "/appointments";
	}

}
