package org.springframework.samples.petclinic.scheduling.request;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.BookingState;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/owner")
public class OwnerSchedulingRequestController {

	private final SchedulingRequestService requestService;

	private final OwnerRepository ownerRepository;

	private final AppointmentRepository appointmentRepository;

	private final Clock clock;

	public OwnerSchedulingRequestController(SchedulingRequestService requestService, OwnerRepository ownerRepository,
			AppointmentRepository appointmentRepository, Clock clock) {
		this.requestService = requestService;
		this.ownerRepository = ownerRepository;
		this.appointmentRepository = appointmentRepository;
		this.clock = clock;
	}

	@GetMapping("/dashboard")
	public String dashboard(Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		List<OwnerRequestProjection> requests = this.requestService.getOwnerRequests(ownerId);
		Instant now = this.clock.instant();
		List<Appointment> appointments = this.appointmentRepository.findByOwnerIdOrderByStartAtDesc(ownerId)
			.stream()
			.filter(appointment -> appointment.getBookingState() == BookingState.CONFIRMED)
			.filter(appointment -> appointment.getStartAt().isAfter(now))
			.sorted(Comparator.comparing(Appointment::getStartAt))
			.toList();
		List<OwnerRequestProjection> itemsNeedingAction = requests.stream()
			.filter(request -> request.rawState() == RequestState.AWAITING_REVIEW
					|| request.rawState() == RequestState.OFFERED || request.awaitingOwnerContact())
			.toList();
		List<OwnerRequestProjection> otherActiveRequests = requests.stream()
			.filter(request -> !request.rawState().isTerminal())
			.filter(request -> !itemsNeedingAction.contains(request))
			.toList();
		List<OwnerRequestProjection> recentHistory = requests.stream()
			.filter(request -> request.rawState().isTerminal())
			.limit(5)
			.toList();

		model.addAttribute("owner", owner);
		model.addAttribute("requests", requests);
		model.addAttribute("appointments", appointments);
		model.addAttribute("itemsNeedingAction", itemsNeedingAction);
		model.addAttribute("otherActiveRequests", otherActiveRequests);
		model.addAttribute("recentHistory", recentHistory);
		return "owner/dashboard";
	}

	@GetMapping("/requests/new")
	public String initNewRequestForm(@RequestParam(name = "petId", required = false) Integer petId,
			Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found"));

		model.addAttribute("owner", owner);
		model.addAttribute("pets", owner.getPets());
		SchedulingRequestForm form = new SchedulingRequestForm();
		if (petId != null) {
			if (owner.getPet(petId) == null) {
				throw new AccessDeniedException("Selected pet does not belong to the authenticated owner");
			}
			form.setPetId(petId);
			List<Appointment> upcoming = this.appointmentRepository.findByPetId(petId)
				.stream()
				.filter(appointment -> appointment.getBookingState() == BookingState.CONFIRMED)
				.filter(appointment -> appointment.getStartAt().isAfter(this.clock.instant()))
				.sorted(Comparator.comparing(Appointment::getStartAt))
				.toList();
			model.addAttribute("selectedPet", owner.getPet(petId));
			model.addAttribute("upcomingAppointments", upcoming);
		}
		model.addAttribute("form", form);
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
