/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.samples.petclinic.security.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OwnerPageController {

	private final OwnerSchedulingAccessService accessService;

	public OwnerPageController(OwnerSchedulingAccessService accessService) {
		this.accessService = accessService;
	}

	@GetMapping("/my/pets")
	public String pets(Model model) {
		Integer ownerId = currentOwnerId();
		model.addAttribute("owner", this.accessService.requireOwner(ownerId));
		return "my/pets";
	}

	@GetMapping("/my/appointments")
	public String appointments(Model model) {
		Integer ownerId = currentOwnerId();
		model.addAttribute("appointments", this.accessService.findAppointments(ownerId));
		return "my/appointments";
	}

	private static Integer currentOwnerId() {
		return SecurityUtils.getCurrentOwnerId().orElseThrow(OwnerResourceNotFoundException::new);
	}

}
