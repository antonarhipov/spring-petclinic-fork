/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.staff;

import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffAppointmentController {

	private final AppointmentLifecycleService lifecycleService;

	private final ClinicSettingsRepository settingsRepository;

	public StaffAppointmentController(AppointmentLifecycleService lifecycleService,
			ClinicSettingsRepository settingsRepository) {
		this.lifecycleService = lifecycleService;
		this.settingsRepository = settingsRepository;
	}

	@GetMapping("/staff/appointments")
	public String showAppointments(Model model) {
		model.addAttribute("appointments", this.lifecycleService.getScheduledAppointments());
		model.addAttribute("clinicZone", this.settingsRepository.getClinicSettings().getZone());
		return "staff/appointments";
	}

	@PostMapping("/staff/appointments/{appointmentId}/cancel")
	public String cancel(@PathVariable Integer appointmentId, @RequestParam String reason,
			RedirectAttributes redirectAttributes) {
		return runAction(() -> this.lifecycleService.cancel(appointmentId, reason), "Appointment cancelled",
				redirectAttributes);
	}

	@PostMapping("/staff/appointments/{appointmentId}/complete")
	public String complete(@PathVariable Integer appointmentId, RedirectAttributes redirectAttributes) {
		return runAction(() -> this.lifecycleService.complete(appointmentId),
				"Appointment completed and visit recorded", redirectAttributes);
	}

	@PostMapping("/staff/appointments/{appointmentId}/no-show")
	public String noShow(@PathVariable Integer appointmentId, RedirectAttributes redirectAttributes) {
		return runAction(() -> this.lifecycleService.markNoShow(appointmentId), "Appointment marked as no-show",
				redirectAttributes);
	}

	private String runAction(Runnable action, String message, RedirectAttributes redirectAttributes) {
		try {
			action.run();
			redirectAttributes.addFlashAttribute("message", message);
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/appointments";
	}

}
