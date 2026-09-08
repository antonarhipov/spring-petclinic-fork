package org.springframework.samples.petclinic.scheduling.appointment;

import java.security.Principal;

import org.springframework.samples.petclinic.scheduling.request.AuthenticatedOwnerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MyAppointmentsController {

	private final OwnerActivityQueryService activity;

	private final AuthenticatedOwnerService owners;

	private final AppointmentService appointments;

	public MyAppointmentsController(OwnerActivityQueryService activity, AuthenticatedOwnerService owners,
			AppointmentService appointments) {
		this.activity = activity;
		this.owners = owners;
		this.appointments = appointments;
	}

	@GetMapping("/my/appointments")
	String appointments(Principal principal, Model model) {
		model.addAttribute("pets", this.activity.activityFor(principal));
		return "my/appointments";
	}

	@GetMapping("/my/appointments/{id}")
	String appointment(@PathVariable int id, Principal principal, Model model) {
		model.addAttribute("appointment", this.activity.appointmentFor(principal, id));
		return "my/appointment-detail";
	}

	@PostMapping("/my/appointments/{id}/cancel")
	String cancel(@PathVariable int id, Principal principal) {
		int ownerId = this.owners.requireOwner(principal).getId();
		this.appointments.cancelByOwner(ownerId, id);
		return "redirect:/my/appointments/" + id;
	}

}
