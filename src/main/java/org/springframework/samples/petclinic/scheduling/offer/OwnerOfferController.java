package org.springframework.samples.petclinic.scheduling.offer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/owner/requests/{requestId}/offers/{offerId}")
public class OwnerOfferController {

	private final OfferService offerService;

	private final OfferRepository offerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public OwnerOfferController(OfferService offerService, OfferRepository offerRepository,
			SchedulingRequestRepository requestRepository, VetRepository vetRepository, Clock clock) {
		this.offerService = offerService;
		this.offerRepository = offerRepository;
		this.requestRepository = requestRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@GetMapping
	public String reviewOffer(@PathVariable("requestId") Long requestId, @PathVariable("offerId") Long offerId,
			Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Offer offer = this.offerRepository.findByIdAndOwnerId(offerId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Offer not found"));

		if (!offer.getRequest().getId().equals(requestId)) {
			throw new IllegalArgumentException("Offer does not match request");
		}

		Vet vet = this.vetRepository.findById(offer.getVetId()).orElse(null);
		String vetName = (vet != null) ? (vet.getFirstName() + " " + vet.getLastName()) : "Veterinarian";

		model.addAttribute("offer", offer);
		model.addAttribute("vetName", vetName);
		model.addAttribute("durationMinutes", Duration.between(offer.getStartAt(), offer.getEndAt()).toMinutes());
		model.addAttribute("currentTime", this.clock.instant());
		return "owner/requests/offer";
	}

	@PostMapping("/accept")
	public String acceptOffer(@PathVariable("requestId") Long requestId, @PathVariable("offerId") Long offerId,
			Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		Appointment appointment = this.offerService.acceptOffer(offerId, ownerId);
		return "redirect:/owner/requests/" + requestId;
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
