/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.staff;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffFallbackController {

	private final StaffFallbackService fallbackService;

	public StaffFallbackController(StaffFallbackService fallbackService) {
		this.fallbackService = fallbackService;
	}

	@GetMapping("/staff/fallback")
	public String showQueue(Model model) {
		model.addAttribute("queue", this.fallbackService.getQueue());
		return "staff/fallbackQueue";
	}

	@PostMapping("/staff/fallback/{requestId}/unblock")
	public String unblock(@PathVariable Integer requestId, RedirectAttributes redirectAttributes) {
		try {
			this.fallbackService.unblock(requestId);
			redirectAttributes.addFlashAttribute("message",
					"Request unblocked. The owner can resume scheduling in the app.");
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/fallback";
	}

}
