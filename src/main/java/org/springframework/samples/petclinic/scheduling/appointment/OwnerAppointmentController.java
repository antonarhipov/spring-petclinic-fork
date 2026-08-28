package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OwnerAppointmentController {

	private final AppointmentService appointments;

	public OwnerAppointmentController(AppointmentService appointments) {
		this.appointments = appointments;
	}

	@PostMapping("/my/appointments/{appointmentId}/cancel")
	public String cancel(@PathVariable Integer appointmentId, @RequestParam(required = false) String reason,
			Authentication actor) {
		this.appointments.cancelOwned(appointmentId, reason, actor);
		return "redirect:/my/appointments";
	}

}
