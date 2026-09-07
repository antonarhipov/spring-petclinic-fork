package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyAppointmentsController {

	@GetMapping("/my/appointments")
	String appointments() {
		return "my/appointments";
	}

}
