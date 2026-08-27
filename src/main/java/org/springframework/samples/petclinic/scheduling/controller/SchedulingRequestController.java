package org.springframework.samples.petclinic.scheduling.controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.dto.SuggestionDto;
import org.springframework.samples.petclinic.scheduling.exception.RateLimitExceededException;
import org.springframework.samples.petclinic.scheduling.matching.MatchingCoordinator;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.RequestStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.AIInterpretationService;
import org.springframework.samples.petclinic.scheduling.service.AvailabilityViewService;
import org.springframework.samples.petclinic.scheduling.service.ReservationService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingRequestService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SchedulingRequestController {

	private final SchedulingRequestService schedulingRequestService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final AIInterpretationService aiInterpretationService;

	private final MatchingCoordinator matchingCoordinator;

	private final ReservationService reservationService;

	private final PetRepository petRepository;

	private final AccountRepository accountRepository;

	private final AvailabilityViewService availabilityViewService;

	public SchedulingRequestController(SchedulingRequestService schedulingRequestService,
			SchedulingRequestRepository schedulingRequestRepository, AIInterpretationService aiInterpretationService,
			MatchingCoordinator matchingCoordinator, ReservationService reservationService, PetRepository petRepository,
			AccountRepository accountRepository, AvailabilityViewService availabilityViewService) {
		this.schedulingRequestService = schedulingRequestService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.aiInterpretationService = aiInterpretationService;
		this.matchingCoordinator = matchingCoordinator;
		this.reservationService = reservationService;
		this.petRepository = petRepository;
		this.accountRepository = accountRepository;
		this.availabilityViewService = availabilityViewService;
	}

	@GetMapping("/scheduling/requests/new")
	public String showNewRequestForm(@RequestParam("petId") Integer petId, Principal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		Pet pet = petRepository.findByIdAndOwnerId(petId, owner.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pet not found"));

		model.addAttribute("pet", pet);
		return "scheduling/requestNew";
	}

	@PostMapping("/scheduling/requests")
	public String submitRequest(@RequestParam("petId") Integer petId, @RequestParam("originalText") String originalText,
			@RequestParam(value = "language", defaultValue = "en") String language, Principal principal,
			RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			SchedulingRequest request = schedulingRequestService.createRequest(owner.getId(), petId, originalText,
					language, principal.getName());
			return "redirect:/scheduling/requests/" + request.getId();
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/new?petId=" + petId;
		}
	}

	@GetMapping("/scheduling/requests/{id}")
	public String showRequest(@PathVariable("id") Integer id, Principal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		SchedulingRequest request = schedulingRequestRepository.findWithDetailsByIdAndOwnerId(id, owner.getId())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

		model.addAttribute("request", request);
		model.addAttribute("availabilityWindows", availabilityViewService.getSymbolicWindows(id));
		model.addAttribute("availabilityIntervals", availabilityViewService.getResolvedIntervals(id));

		if (request.getStatus() == RequestStatus.SLOT_HELD) {
			SuggestionDto suggestion = reservationService.getGuidedHoldSuggestion(id, owner.getId());
			model.addAttribute("suggestion", suggestion);
		}
		else if (request.getStatus() == RequestStatus.STAFF_QUEUED
				|| request.getStatus() == RequestStatus.STAFF_OFFERED) {
			SuggestionDto offer = reservationService.getGuidedHoldSuggestion(id, owner.getId());
			model.addAttribute("offer", offer);
		}

		return "scheduling/requestStatus";
	}

	@PostMapping("/scheduling/requests/{id}/consent")
	public String submitConsent(@PathVariable("id") Integer id, @RequestParam("consent") boolean consent,
			Principal principal, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			String token = schedulingRequestService.recordConsentAndDispatchAI(id, owner.getId(), consent,
					principal.getName());
			if (token != null) {
				aiInterpretationService.asyncInterpret(token, id);
			}
			return "redirect:/scheduling/requests/" + id;
		}
		catch (RateLimitExceededException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@GetMapping("/scheduling/requests/{id}/status")
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getStatusJson(@PathVariable("id") Integer id, Principal principal) {
		Owner owner = getAuthenticatedOwner(principal);
		Optional<SchedulingRequest> reqOpt = schedulingRequestRepository.findByIdAndOwnerId(id, owner.getId());
		if (reqOpt.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		SchedulingRequest request = reqOpt.get();

		Map<String, Object> body = new HashMap<>();
		body.put("id", request.getId());
		body.put("status", request.getStatus().name());
		body.put("urgent", request.isUrgent());
		body.put("aiSummary", request.getAiSummary());
		body.put("lastClarificationReason", request.getLastClarificationReason());

		return ResponseEntity.ok(body);
	}

	@PostMapping("/scheduling/requests/{id}/confirm")
	public String confirmRequest(@PathVariable("id") Integer id,
			@RequestParam(value = "confirmUnrestricted", defaultValue = "false") boolean confirmUnrestricted,
			Principal principal, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			schedulingRequestService.confirmInterpretation(id, owner.getId(), confirmUnrestricted, principal.getName());
			matchingCoordinator.dispatchMatching(id, principal.getName());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@PostMapping("/scheduling/requests/{id}/ask-again")
	public String askAgain(@PathVariable("id") Integer id, Principal principal, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			matchingCoordinator.dispatchMatching(id, principal.getName());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@GetMapping("/scheduling/requests/{id}/suggestion")
	@ResponseBody
	public ResponseEntity<SuggestionDto> getSuggestionJson(@PathVariable("id") Integer id, Principal principal) {
		Owner owner = getAuthenticatedOwner(principal);
		SuggestionDto suggestion = reservationService.getGuidedHoldSuggestion(id, owner.getId());
		if (suggestion == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok(suggestion);
	}

	@PostMapping("/scheduling/requests/{id}/accept-suggestion")
	public String acceptSuggestion(@PathVariable("id") Integer id, @RequestParam("reservationId") Integer reservationId,
			@RequestParam(value = "requestVersion", required = false) Long requestVersion, Principal principal,
			RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			reservationService.acceptGuidedHold(id, owner.getId(), reservationId, requestVersion, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Appointment confirmed successfully!");
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@PostMapping("/scheduling/requests/{id}/reject-suggestion")
	public String rejectSuggestion(@PathVariable("id") Integer id, @RequestParam("reservationId") Integer reservationId,
			@RequestParam(value = "requestVersion", required = false) Long requestVersion, Principal principal,
			RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			reservationService.rejectGuidedHold(id, owner.getId(), reservationId, requestVersion, principal.getName());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@PostMapping("/scheduling/requests/{id}/accept-offer")
	public String acceptOffer(@PathVariable("id") Integer id, @RequestParam("reservationId") Integer reservationId,
			@RequestParam(value = "requestVersion", required = false) Long requestVersion, Principal principal,
			RedirectAttributes redirectAttributes) {
		return acceptSuggestion(id, reservationId, requestVersion, principal, redirectAttributes);
	}

	@PostMapping("/scheduling/requests/{id}/reject-offer")
	public String rejectOffer(@PathVariable("id") Integer id, @RequestParam("reservationId") Integer reservationId,
			@RequestParam(value = "requestVersion", required = false) Long requestVersion, Principal principal,
			RedirectAttributes redirectAttributes) {
		return rejectSuggestion(id, reservationId, requestVersion, principal, redirectAttributes);
	}

	@PostMapping("/scheduling/requests/{id}/dispute")
	public String disputeRequest(@PathVariable("id") Integer id,
			@RequestParam(value = "reason", required = false) String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			schedulingRequestService.disputeClinicalFacts(id, owner.getId(), reason, principal.getName());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@PostMapping("/scheduling/requests/{id}/replace-text")
	public String replaceText(@PathVariable("id") Integer id, @RequestParam("newText") String newText,
			Principal principal, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			schedulingRequestService.replaceOriginalText(id, owner.getId(), newText, principal.getName());
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	@PostMapping("/scheduling/requests/{id}/cancel")
	public String cancelRequest(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			schedulingRequestService.cancelRequest(id, owner.getId(), principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Request cancelled successfully");
			return "redirect:/scheduling/requests/" + id;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/requests/" + id;
		}
	}

	private Owner getAuthenticatedOwner(Principal principal) {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
		}
		Account account = accountRepository.findByUsernameIgnoreCase(principal.getName())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));

		if (account.getOwner() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not an owner account");
		}
		return account.getOwner();
	}

}
