/*
 * Copyright 2012-2025 the original author or authors.
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
package org.springframework.samples.petclinic.appointment;

import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.solver.AcceptResult;
import org.springframework.samples.petclinic.scheduling.solver.SuggestionResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Owner-facing entry point for self-scheduling. Every mapping is owner-scoped through
 * {@code @ownerSecurity.canAccessOwner}, so an owner can only initiate and drive requests
 * for their own pets. It orchestrates the existing guided flow via
 * {@link OwnerSchedulingService}; no new scheduling behavior lives here.
 */
@Controller
public class OwnerSchedulingController {

	private final OwnerSchedulingService schedulingService;

	private final OwnerRepository ownerRepository;

	private final ClinicSettingsRepository settingsRepository;

	public OwnerSchedulingController(OwnerSchedulingService schedulingService, OwnerRepository ownerRepository,
			ClinicSettingsRepository settingsRepository) {
		this.schedulingService = schedulingService;
		this.ownerRepository = ownerRepository;
		this.settingsRepository = settingsRepository;
	}

	@GetMapping("/owners/{ownerId}/scheduling/new")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String initNewRequest(@PathVariable Integer ownerId, Model model) {
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		model.addAttribute("owner", owner);
		model.addAttribute("ownerId", ownerId);
		return "owners/scheduling/newRequest";
	}

	@PostMapping("/owners/{ownerId}/scheduling")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String createRequest(@PathVariable Integer ownerId, @RequestParam Integer petId,
			@RequestParam String freeText, RedirectAttributes redirectAttributes) {
		try {
			AppointmentRequest request = this.schedulingService.startRequest(ownerId, petId, freeText);
			return "redirect:/owners/" + ownerId + "/scheduling/" + request.getId();
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
			return "redirect:/owners/" + ownerId + "/scheduling/new";
		}
	}

	@GetMapping("/owners/{ownerId}/scheduling/{requestId}")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String showRequest(@PathVariable Integer ownerId, @PathVariable Integer requestId, Model model) {
		AppointmentRequest request = this.schedulingService.getRequest(ownerId, requestId);
		model.addAttribute("ownerId", ownerId);
		model.addAttribute("request", request);
		model.addAttribute("interpretation", this.schedulingService.interpretationFor(request));
		model.addAttribute("clinicZone", this.settingsRepository.getClinicSettings().getZone());
		return "owners/scheduling/request";
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/consent")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String consent(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			this.schedulingService.grantConsent(ownerId, requestId);
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/decline")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String decline(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			this.schedulingService.declineConsent(ownerId, requestId);
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/confirm")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String confirm(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			SuggestionResult result = this.schedulingService.confirmAndSuggest(ownerId, requestId);
			redirectAttributes.addFlashAttribute("message", result.message());
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/accept")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String accept(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			AcceptResult result = this.schedulingService.accept(ownerId, requestId);
			redirectAttributes.addFlashAttribute("message", result.message());
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/reject")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String reject(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			SuggestionResult result = this.schedulingService.reject(ownerId, requestId);
			redirectAttributes.addFlashAttribute("message", result.message());
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	@PostMapping("/owners/{ownerId}/scheduling/{requestId}/ask-again")
	@PreAuthorize("@ownerSecurity.canAccessOwner(#ownerId, authentication)")
	public String askAgain(@PathVariable Integer ownerId, @PathVariable Integer requestId,
			RedirectAttributes redirectAttributes) {
		try {
			SuggestionResult result = this.schedulingService.askAgain(ownerId, requestId);
			redirectAttributes.addFlashAttribute("message", result.message());
		}
		catch (RuntimeException ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return redirectToRequest(ownerId, requestId);
	}

	private String redirectToRequest(Integer ownerId, Integer requestId) {
		return "redirect:/owners/" + ownerId + "/scheduling/" + requestId;
	}

}
