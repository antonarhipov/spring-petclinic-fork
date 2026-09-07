package org.springframework.samples.petclinic.scheduling.appointment;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyAppointmentsController {

	private final OwnerActivityQueryService activity;

	public MyAppointmentsController(OwnerActivityQueryService activity) {
		this.activity = activity;
	}

	@GetMapping("/my/appointments")
	String appointments(Principal principal, Model model) {
		model.addAttribute("pets", this.activity.activityFor(principal));
		return "my/appointments";
	}

}
