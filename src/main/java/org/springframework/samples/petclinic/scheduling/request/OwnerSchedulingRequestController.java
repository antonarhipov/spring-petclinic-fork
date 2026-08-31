package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/owner")
public class OwnerSchedulingRequestController {

	private final SchedulingRequestService requestService;

	private final OwnerRepository ownerRepository;

	private final AppointmentRepository appointmentRepository;

	public OwnerSchedulingRequestController(SchedulingRequestService requestService, OwnerRepository ownerRepository,
			AppointmentRepository appointmentRepository) {
		this.requestService = requestService;
		this.ownerRepository = ownerRepository;
		this.appointmentRepository = appointmentRepository;
	}

	@GetMapping("/dashboard")
	public String dashboard(Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		List<OwnerRequestProjection> requests = this.requestService.getOwnerRequests(ownerId);
		List<Appointment> appointments = this.appointmentRepository.findByOwnerIdOrderByStartAtDesc(ownerId);

		model.addAttribute("owner", owner);
		model.addAttribute("requests", requests);
		model.addAttribute("appointments", appointments);
		return "owner/dashboard";
	}

	@GetMapping("/requests/new")
	public String initNewRequestForm(Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		model.addAttribute("owner", owner);
		model.addAttribute("pets", owner.getPets());
		model.addAttribute("form", new SchedulingRequestForm());
		return "owner/requests/new";
	}

	@PostMapping("/requests")
	public String processNewRequest(@Valid @ModelAttribute("form") SchedulingRequestForm form, BindingResult result,
			Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		if (result.hasErrors()) {
			model.addAttribute("owner", owner);
			model.addAttribute("pets", owner.getPets());
			return "owner/requests/new";
		}

		try {
			SchedulingRequest request = this.requestService.submitRequest(ownerId, form.getPetId(), form.getProse(),
					form.isAiConsent());
			return "redirect:/owner/requests/" + request.getId();
		}
		catch (IllegalArgumentException ex) {
			result.reject("error.request", ex.getMessage());
			model.addAttribute("owner", owner);
			model.addAttribute("pets", owner.getPets());
			return "owner/requests/new";
		}
	}

	@GetMapping("/requests/{requestId}")
	public String requestDetail(@PathVariable("requestId") Long requestId, Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		OwnerRequestProjection projection = this.requestService.getOwnerRequestProjection(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		model.addAttribute("request", projection);
		return "owner/requests/detail";
	}

	@GetMapping("/requests/{requestId}/status")
	@ResponseBody
	public ResponseEntity<RequestStatusResponse> requestStatus(@PathVariable("requestId") Long requestId,
			Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		return this.requestService.getOwnerRequestStatus(requestId, ownerId)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	private Integer extractOwnerId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.getOwnerId() != null) {
				return principal.getOwnerId();
			}
		}
		throw new AccessDeniedException("Authenticated owner principal required");
	}

}
