/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigService;
import org.springframework.samples.petclinic.scheduling.interpretation.RequestInterpretationService;
import org.springframework.samples.petclinic.scheduling.request.ActiveRequestExistsException;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.security.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OwnerRequestController {

	private final OwnerSchedulingAccessService accessService;

	private final RequestLifecycleService lifecycleService;

	private final RequestInterpretationService interpretationService;

	private final SuggestionService suggestionService;

	private final ClinicConfigService clinicConfigService;

	public OwnerRequestController(OwnerSchedulingAccessService accessService, RequestLifecycleService lifecycleService,
			RequestInterpretationService interpretationService, SuggestionService suggestionService,
			ClinicConfigService clinicConfigService) {
		this.accessService = accessService;
		this.lifecycleService = lifecycleService;
		this.interpretationService = interpretationService;
		this.suggestionService = suggestionService;
		this.clinicConfigService = clinicConfigService;
	}

	@GetMapping("/my/requests/new")
	public String newRequest(Model model) {
		Integer ownerId = ownerId();
		model.addAttribute("pets", this.accessService.findPetsWithoutActiveRequest(ownerId));
		addEmergencyPhone(model);
		return "my/requestForm";
	}

	@PostMapping("/my/requests")
	public String create(@RequestParam Integer petId, @RequestParam String reasonText,
			@RequestParam String availabilityText, RedirectAttributes redirectAttributes) {
		Integer ownerId = ownerId();
		try {
			SchedulingRequest request = this.lifecycleService.createRequest(this.accessService.requireOwner(ownerId),
					this.accessService.requirePet(ownerId, petId), reasonText, availabilityText,
					SecurityUtils.getCurrentUsername().orElseThrow());
			return "redirect:/my/requests/" + request.getId();
		}
		catch (ActiveRequestExistsException ex) {
			redirectAttributes.addFlashAttribute("activeRequestUnavailable", true);
			return "redirect:/my/requests/new";
		}
	}

	@GetMapping("/my/requests/{requestId}")
	public String detail(@PathVariable Integer requestId, Model model) {
		SchedulingRequest request = this.accessService.requireRequest(ownerId(), requestId);
		model.addAttribute("request", request);
		this.interpretationService.latest(requestId).ifPresent(value -> model.addAttribute("interpretation", value));
		addEmergencyPhone(model);
		return "my/requestDetail";
	}

	@PostMapping("/my/requests/{requestId}/consent")
	public String consent(@PathVariable Integer requestId) {
		SchedulingRequest request = this.accessService.requireRequest(ownerId(), requestId);
		String actor = SecurityUtils.getCurrentUsername().orElseThrow();
		this.lifecycleService.consent(request, actor);
		this.interpretationService.interpret(request, actor);
		return "redirect:/my/requests/" + requestId;
	}

	@PostMapping("/my/requests/{requestId}/decline")
	public String decline(@PathVariable Integer requestId) {
		this.lifecycleService.declineConsent(this.accessService.requireRequest(ownerId(), requestId),
				SecurityUtils.getCurrentUsername().orElseThrow());
		return "redirect:/my/requests/" + requestId;
	}

	@PostMapping("/my/requests/{requestId}/confirm")
	public String confirm(@PathVariable Integer requestId) {
		this.suggestionService.confirm(this.accessService.requireRequest(ownerId(), requestId),
				SecurityUtils.getCurrentUsername().orElseThrow());
		return "redirect:/my/requests/" + requestId;
	}

	@PostMapping("/my/requests/{requestId}/accept")
	public String accept(@PathVariable Integer requestId) {
		this.suggestionService.accept(this.accessService.requireRequest(ownerId(), requestId),
				SecurityUtils.getCurrentUsername().orElseThrow());
		return "redirect:/my/appointments";
	}

	private void addEmergencyPhone(Model model) {
		model.addAttribute("emergencyPhone", this.clinicConfigService.current().getEmergencyPhone());
	}

	private static Integer ownerId() {
		return SecurityUtils.getCurrentOwnerId().orElseThrow(OwnerResourceNotFoundException::new);
	}

}
