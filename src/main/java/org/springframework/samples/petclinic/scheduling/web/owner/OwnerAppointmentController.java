package org.springframework.samples.petclinic.scheduling.web.owner;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.appointment.OwnerAppointmentService;
import org.springframework.samples.petclinic.scheduling.request.OwnerSchedulingQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class OwnerAppointmentController {

	private final CurrentOwnerAccount currentOwner;

	private final OwnerSchedulingQueryService queries;

	private final OwnerAppointmentService cancellations;

	public OwnerAppointmentController(CurrentOwnerAccount currentOwner, OwnerSchedulingQueryService queries,
			OwnerAppointmentService cancellations) {
		this.currentOwner = currentOwner;
		this.queries = queries;
		this.cancellations = cancellations;
	}

	@GetMapping("/owner/appointments")
	public String list(Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("appointments", this.queries.appointments(account.getOwnerId()));
		return "scheduling/owner/appointments";
	}

	@GetMapping("/owner/appointments/{id}")
	public String detail(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("appointment", this.queries.appointment(account.getOwnerId(), id));
		return "scheduling/owner/appointment-detail";
	}

	@GetMapping("/owner/appointments/{id}/cancel")
	public String cancelDialog(@PathVariable Long id, Authentication authentication, Model model) {
		Account account = this.currentOwner.require(authentication);
		model.addAttribute("appointment", this.queries.appointment(account.getOwnerId(), id));
		model.addAttribute("form", new OwnerCancellationForm());
		return "scheduling/owner/cancel-appointment-dialog";
	}

	@PostMapping("/owner/appointments/{id}/cancel")
	public String cancel(@PathVariable Long id, @ModelAttribute OwnerCancellationForm form,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		this.cancellations.cancelBeforeStart(id, account.getOwnerId(), account.getId(), form.getReason());
		return "redirect:/owner/appointments";
	}

}
