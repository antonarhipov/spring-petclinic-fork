package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.appointment.OfferAcceptanceService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferDecisionService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferService;
import org.springframework.samples.petclinic.scheduling.appointment.OfferStatus;
import org.springframework.samples.petclinic.scheduling.appointment.OfferUnavailableException;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OwnerOfferController {

	private final CurrentOwnerAccount currentOwner;

	private final OfferService offers;

	private final OfferAcceptanceService acceptance;

	private final OfferDecisionService decisions;

	private final VetRepository vets;

	public OwnerOfferController(CurrentOwnerAccount currentOwner, OfferService offers,
			OfferAcceptanceService acceptance, OfferDecisionService decisions, VetRepository vets) {
		this.currentOwner = currentOwner;
		this.offers = offers;
		this.acceptance = acceptance;
		this.decisions = decisions;
		this.vets = vets;
	}

	@GetMapping("/owner/scheduling-requests/{id}/offers/{offerId}")
	public String offer(@PathVariable Long id, @PathVariable Long offerId, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		OfferService.OfferView view = this.offers.loadOwned(id, account.getOwnerId(), offerId);
		boolean expired = view.hold() != null && !view.hold().getExpiresAt().isAfter(java.time.Instant.now());
		if (view.offer().getStatus() != OfferStatus.HELD || expired) {
			model.addAttribute("request", view.request());
			model.addAttribute("offer", view.offer());
			return "scheduling/owner/offer-unavailable";
		}
		model.addAttribute("request", view.request());
		model.addAttribute("offer", view.offer());
		model.addAttribute("hold", view.hold());
		model.addAttribute("veterinarianName", veterinarianName(view.offer().getVeterinarianId()));
		return "scheduling/owner/offer";
	}

	@PostMapping("/owner/scheduling-requests/{id}/offers/{offerId}/accept")
	public String accept(@PathVariable Long id, @PathVariable Long offerId, @RequestParam Integer expectedVersion,
			Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		try {
			this.acceptance.accept(id, account.getOwnerId(), offerId, expectedVersion);
			return "redirect:/owner/dashboard";
		}
		catch (OfferUnavailableException ex) {
			model.addAttribute("request", this.offers.loadOwned(id, account.getOwnerId(), offerId).request());
			return "scheduling/owner/offer-unavailable";
		}
	}

	@PostMapping("/owner/scheduling-requests/{id}/offers/{offerId}/reject")
	public String reject(@PathVariable Long id, @PathVariable Long offerId, @RequestParam Integer expectedVersion,
			@RequestParam(required = false) String reason, Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.decisions.reject(id, account.getOwnerId(), offerId, expectedVersion, reason);
		return "redirect:/owner/scheduling-requests/" + id + "/suggestion";
	}

	private String veterinarianName(Integer veterinarianId) {
		return this.vets.findAll()
			.stream()
			.filter(vet -> veterinarianId.equals(vet.getId()))
			.findFirst()
			.map(vet -> vet.getFirstName() + " " + vet.getLastName())
			.orElse("");
	}

}
