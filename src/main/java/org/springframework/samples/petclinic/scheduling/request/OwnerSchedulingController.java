package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.samples.petclinic.scheduling.appointment.OwnerAppointmentQueryService;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;
import org.springframework.samples.petclinic.security.OwnerAccessService;

@Controller
public class OwnerSchedulingController {

	private final SchedulingRequestService requests;

	private final SchedulingRequestQueryService query;

	private final OwnerAccessService owners;

	private final UrgentCareGuidanceService guidance;

	private final OfferService offers;

	private final AppointmentOfferRepository offerRepository;

	private final OwnerAppointmentQueryService appointments;

	public OwnerSchedulingController(SchedulingRequestService requests, SchedulingRequestQueryService query,
			OwnerAccessService owners, UrgentCareGuidanceService guidance, OfferService offers,
			AppointmentOfferRepository offerRepository, OwnerAppointmentQueryService appointments) {
		this.requests = requests;
		this.query = query;
		this.owners = owners;
		this.guidance = guidance;
		this.offers = offers;
		this.offerRepository = offerRepository;
		this.appointments = appointments;
	}

	@GetMapping("/my/appointments")
	public String dashboard(Authentication actor, Model model) {
		model.addAttribute("appointments", this.appointments.upcoming(actor));
		model.addAttribute("requests", this.query.history(actor));
		return "scheduling/dashboard";
	}

	@GetMapping("/my/scheduling/requests/new")
	public String newRequest(Authentication actor, Model model) {
		model.addAttribute("pets", this.owners.currentOwner(actor).owner().getPets());
		model.addAttribute("guidance", this.guidance.guidance());
		return "scheduling/newRequest";
	}

	@PostMapping("/my/scheduling/requests")
	public String submit(@RequestParam Integer petId, @RequestParam String sourceText,
			@RequestParam(defaultValue = "false") boolean consent, Authentication actor,
			RedirectAttributes attributes) {
		try {
			SchedulingRequest request = this.requests.submit(petId, sourceText, consent, actor);
			return request.getState() == SchedulingRequestState.STAFF_HANDLING ? "redirect:/my/appointments"
					: "redirect:/my/scheduling/requests/" + request.getId() + "/review";
		}
		catch (RuntimeException ex) {
			attributes.addFlashAttribute("error", ex.getMessage());
			return "redirect:/my/scheduling/requests/new";
		}
	}

	@GetMapping("/my/scheduling/requests/{requestId}/review")
	public String review(@PathVariable Integer requestId, Authentication actor, Model model) {
		model.addAttribute("request", this.query.ownedRequest(requestId, actor));
		return "scheduling/reviewRequest";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/confirm")
	public String confirm(@PathVariable Integer requestId, @RequestParam(required = false) String visitReason,
			@RequestParam(required = false) Integer duration, Authentication actor) {
		SchedulingRequest request = this.requests.confirm(requestId, visitReason, duration, actor);
		if (request.getState() == SchedulingRequestState.OFFER_HELD) {
			return "redirect:/my/scheduling/requests/" + requestId + "/offer";
		}
		return request.getState() == SchedulingRequestState.READY_FOR_SUGGESTION
				? "redirect:/my/scheduling/requests/" + requestId + "/no-preferred-availability"
				: "redirect:/my/appointments";
	}

	@GetMapping("/my/scheduling/requests/{requestId}/no-preferred-availability")
	public String noPreferredAvailability(@PathVariable Integer requestId, Authentication actor, Model model) {
		SchedulingRequest request = this.query.ownedRequest(requestId, actor);
		if (request.getState() != SchedulingRequestState.READY_FOR_SUGGESTION
				|| !request.getCurrentRevision().hasPreferredWindows()) {
			return "redirect:/my/appointments";
		}
		model.addAttribute("request", request);
		return "scheduling/noPreferredAvailability";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/alternative")
	public String alternative(@PathVariable Integer requestId, Authentication actor, RedirectAttributes attributes) {
		if (this.requests.offerAlternative(requestId, actor)) {
			return "redirect:/my/scheduling/requests/" + requestId + "/offer";
		}
		attributes.addFlashAttribute("noAlternative", true);
		return "redirect:/my/scheduling/requests/" + requestId + "/no-preferred-availability";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/staff")
	public String staff(@PathVariable Integer requestId, Authentication actor) {
		this.requests.routeToStaff(requestId, actor);
		return "redirect:/my/appointments";
	}

	@GetMapping("/my/scheduling/requests/{requestId}/offer")
	public String offer(@PathVariable Integer requestId, Authentication actor, Model model) {
		SchedulingRequest request = this.query.ownedRequest(requestId, actor);
		model.addAttribute("request", request);
		model.addAttribute("offer",
				this.offerRepository
					.findFirstByRevisionIdAndStateOrderByOfferedAtDesc(request.getCurrentRevision().getId(),
							OfferState.HELD)
					.orElseThrow(() -> new IllegalArgumentException("No held offer")));
		return "scheduling/offer";
	}

	@PostMapping("/my/scheduling/offers/{offerId}/accept")
	public String accept(@PathVariable Integer offerId, Authentication actor, RedirectAttributes attributes) {
		try {
			this.offers.accept(offerId, this.owners.currentOwner(actor).owner().getId(), actor);
			return "redirect:/my/appointments";
		}
		catch (RuntimeException ex) {
			attributes.addFlashAttribute("error", "This offer is no longer available.");
			return "redirect:/my/appointments";
		}
	}

	@PostMapping("/my/scheduling/offers/{offerId}/reject")
	public String reject(@PathVariable Integer offerId, @RequestParam(required = false) String reason,
			Authentication actor) {
		this.offers.reject(offerId, this.owners.currentOwner(actor).owner().getId(), reason, actor);
		return "redirect:/my/appointments";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/next")
	public String next(@PathVariable Integer requestId, Authentication actor) {
		SchedulingRequest request = this.query.ownedRequest(requestId, actor);
		if (this.offers.createOffer(request.getCurrentRevision(), actor).isPresent()) {
			return "redirect:/my/scheduling/requests/" + requestId + "/offer";
		}
		return "redirect:/my/appointments";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/revise")
	public String revise(@PathVariable Integer requestId, @RequestParam String sourceText,
			@RequestParam(defaultValue = "false") boolean consent, Authentication actor) {
		this.requests.revise(requestId, sourceText, consent, actor);
		return "redirect:/my/scheduling/requests/" + requestId + "/review";
	}

	@PostMapping("/my/scheduling/requests/{requestId}/withdraw")
	public String withdraw(@PathVariable Integer requestId, Authentication actor) {
		this.requests.withdraw(requestId, actor);
		return "redirect:/my/appointments";
	}

	@GetMapping("/my/scheduling/requests")
	public String history(Authentication actor, Model model) {
		model.addAttribute("requests", this.query.history(actor));
		return "scheduling/requestHistory";
	}

}
