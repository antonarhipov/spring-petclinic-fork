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

package org.springframework.samples.petclinic.scheduling;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.ai.Interpretation;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.security.UserAccount;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SchedulingController {

	private final SchedulingRequestRepository schedulingRequests;

	private final OwnerRepository owners;

	private final SchedulingOrchestrator orchestrator;

	private final HoldService holdService;

	private final BookingService bookingService;

	private final UserAccountRepository userAccountRepository;

	public SchedulingController(SchedulingRequestRepository schedulingRequests, OwnerRepository owners,
			SchedulingOrchestrator orchestrator, HoldService holdService, BookingService bookingService,
			UserAccountRepository userAccountRepository) {
		this.schedulingRequests = schedulingRequests;
		this.owners = owners;
		this.orchestrator = orchestrator;
		this.holdService = holdService;
		this.bookingService = bookingService;
		this.userAccountRepository = userAccountRepository;
	}

	private Authentication getAuthentication(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getPrincipal())) {
			return authentication;
		}
		Authentication holderAuth = SecurityContextHolder.getContext().getAuthentication();
		if (holderAuth != null && holderAuth.isAuthenticated() && !"anonymousUser".equals(holderAuth.getPrincipal())) {
			return holderAuth;
		}
		return null;
	}

	private void checkRequestOwnership(SchedulingRequest request, Authentication authentication) {
		Authentication auth = getAuthentication(authentication);
		if (auth == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
		}
		if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"))) {
			return;
		}
		String username = auth.getName();
		UserAccount account = this.userAccountRepository.findByUsername(username).orElse(null);
		if (account == null || account.getOwner() == null || request.getOwner() == null
				|| !account.getOwner().getId().equals(request.getOwner().getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Access denied: you do not own this scheduling request");
		}
	}

	private Owner resolveOwner(Integer ownerId, Authentication authentication) {
		Authentication auth = getAuthentication(authentication);
		if (auth == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
		}
		boolean isStaff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF"));
		if (ownerId == null) {
			UserAccount account = this.userAccountRepository.findByUsername(auth.getName()).orElse(null);
			if (account == null || account.getOwner() == null) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No owner profile linked to this account");
			}
			return account.getOwner();
		}
		if (!isStaff) {
			UserAccount account = this.userAccountRepository.findByUsername(auth.getName()).orElse(null);
			if (account == null || account.getOwner() == null || !account.getOwner().getId().equals(ownerId)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: owner ID mismatch");
			}
			return account.getOwner();
		}
		return this.owners.findById(ownerId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found"));
	}

	@GetMapping({ "/owners/{ownerId}/pets/{petId}/schedule/new", "/my-pets/{petId}/schedule/new" })
	public String initCreationForm(@PathVariable(value = "ownerId", required = false) Integer ownerId,
			@PathVariable("petId") int petId, Authentication authentication, Model model) {
		Optional<SchedulingRequest> activeRequest = this.schedulingRequests.findByActivePetKey(petId);
		if (activeRequest.isPresent()) {
			return "redirect:/scheduling/requests/" + activeRequest.get().getId();
		}

		Owner owner = resolveOwner(ownerId, authentication);
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: pet not found for owner");
		}

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("rawText", "");
		model.addAttribute("aiConsent", false);
		return "scheduling/requestForm";
	}

	@PostMapping({ "/owners/{ownerId}/pets/{petId}/schedule/new", "/my-pets/{petId}/schedule/new" })
	public String processCreationForm(@PathVariable(value = "ownerId", required = false) Integer ownerId,
			@PathVariable("petId") int petId, @RequestParam("rawText") String rawText,
			@RequestParam(value = "aiConsent", defaultValue = "false") boolean aiConsent,
			Authentication authentication) {
		Optional<SchedulingRequest> activeRequest = this.schedulingRequests.findByActivePetKey(petId);
		if (activeRequest.isPresent()) {
			return "redirect:/scheduling/requests/" + activeRequest.get().getId();
		}

		Owner owner = resolveOwner(ownerId, authentication);
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: pet not found for owner");
		}

		SchedulingRequest request = new SchedulingRequest();
		request.setOwner(owner);
		request.setPet(pet);
		request.setRawText(rawText);
		request.setAiConsent(aiConsent);
		request.setState(RequestState.DRAFT);
		SchedulingRequest saved = this.schedulingRequests.saveAndFlush(request);

		if (!aiConsent) {
			saved.transitionTo(RequestState.STAFF_QUEUED, QueueReason.CONSENT_DECLINED);
			this.schedulingRequests.saveAndFlush(saved);
		}
		else {
			this.orchestrator.processInterpretationAsync(saved.getId());
		}

		return "redirect:/scheduling/requests/" + saved.getId();
	}

	@GetMapping("/scheduling/requests/{requestId}")
	public String showStatus(@PathVariable("requestId") int requestId, Authentication authentication, Model model) {
		SchedulingRequest request = this.schedulingRequests.findByIdWithOwnerAndPet(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		Optional<Interpretation> interpretation = this.orchestrator.getInterpretation(request);
		Optional<org.springframework.samples.petclinic.scheduling.model.Hold> activeHold = this.holdService
			.getActiveHold(requestId);
		Optional<org.springframework.samples.petclinic.scheduling.model.Appointment> appointment = this.bookingService
			.findAppointmentForRequest(requestId);

		model.addAttribute("request", request);
		model.addAttribute("owner", request.getOwner());
		model.addAttribute("pet", request.getPet());
		model.addAttribute("interpretation", interpretation.orElse(null));
		model.addAttribute("heldSlot", activeHold.map(this.holdService::toSuggestionView).orElse(null));
		model.addAttribute("appointment", appointment.orElse(null));
		return "scheduling/status";
	}

	@PostMapping("/scheduling/requests/{requestId}/accept")
	public String acceptSlot(@PathVariable("requestId") int requestId, Authentication authentication) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		try {
			this.bookingService.acceptHold(requestId);
		}
		catch (IllegalStateException ex) {
			// If hold expired or not found, status page will reflect the new state
			// (SUGGESTING)
		}
		return "redirect:/scheduling/requests/" + requestId;
	}

	@PostMapping("/scheduling/requests/{requestId}/reject")
	public String rejectSlot(@PathVariable("requestId") int requestId, Authentication authentication) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		Optional<org.springframework.samples.petclinic.scheduling.model.Hold> activeHold = this.holdService
			.getActiveHold(requestId);
		if (activeHold.isPresent()) {
			org.springframework.samples.petclinic.scheduling.model.Hold hold = activeHold.get();
			this.holdService.recordExclusion(request, hold.getVet(), hold.getStartTime());
			this.holdService.releaseHold(hold);
			SchedulingRequest fresh = this.schedulingRequests.findById(requestId).orElse(null);
			if (fresh != null && fresh.getState() == RequestState.SLOT_HELD) {
				fresh.transitionTo(RequestState.SUGGESTING, null);
				this.schedulingRequests.saveAndFlush(fresh);
				this.orchestrator.processSolveAsync(fresh.getId());
			}
		}
		return "redirect:/scheduling/requests/" + requestId;
	}

	@PostMapping("/scheduling/requests/{requestId}/cancel")
	public String cancelRequest(@PathVariable("requestId") int requestId, Authentication authentication) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		if (!request.getState().isTerminal()) {
			Optional<org.springframework.samples.petclinic.scheduling.model.Hold> activeHold = this.holdService
				.getActiveHold(requestId);
			if (activeHold.isPresent()) {
				this.holdService.releaseHold(activeHold.get());
			}
			request.cancel();
			this.schedulingRequests.saveAndFlush(request);
		}

		return "redirect:/scheduling/requests/" + requestId;
	}

	@PostMapping("/scheduling/requests/{requestId}/confirm")
	public String confirmInterpretation(@PathVariable("requestId") int requestId, Authentication authentication) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		if (request.getState() == RequestState.AWAITING_CONFIRMATION) {
			request.transitionTo(RequestState.SUGGESTING, null);
			this.schedulingRequests.saveAndFlush(request);
			this.orchestrator.processSolveAsync(request.getId());
		}

		return "redirect:/scheduling/requests/" + requestId;
	}

	@PostMapping("/scheduling/requests/{requestId}/edit-text")
	public String editText(@PathVariable("requestId") int requestId, @RequestParam("rawText") String rawText,
			@RequestParam(value = "aiConsent", defaultValue = "false") boolean aiConsent,
			Authentication authentication) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		checkRequestOwnership(request, authentication);

		if (request.getState() == RequestState.AWAITING_CONFIRMATION || request.getState() == RequestState.DRAFT) {
			request.setRawText(rawText);
			request.setAiConsent(aiConsent);
			request.setInterpretationJson(null);
			request.transitionTo(RequestState.DRAFT, null);
			this.schedulingRequests.saveAndFlush(request);

			if (aiConsent) {
				this.orchestrator.processInterpretationAsync(request.getId());
			}
			else {
				request.transitionTo(RequestState.STAFF_QUEUED, QueueReason.CONSENT_DECLINED);
				this.schedulingRequests.saveAndFlush(request);
			}
		}

		return "redirect:/scheduling/requests/" + requestId;
	}

}
