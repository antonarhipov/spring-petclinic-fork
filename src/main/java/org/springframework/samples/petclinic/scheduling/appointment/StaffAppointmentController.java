package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffAppointmentController {

	private final AppointmentService appointments;

	public StaffAppointmentController(AppointmentService appointments) {
		this.appointments = appointments;
	}

	@PostMapping("/staff/appointments/{id}/complete")
	String complete(@PathVariable int id, @RequestParam String description) {
		this.appointments.complete(id, description);
		return "redirect:/staff/queue";
	}

	@PostMapping("/staff/appointments/{id}/no-show")
	String noShow(@PathVariable int id) {
		this.appointments.markNoShow(id);
		return "redirect:/staff/queue";
	}

}
