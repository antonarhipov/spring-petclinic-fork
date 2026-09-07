package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;

import org.springframework.samples.petclinic.scheduling.appointment.OwnerActivityQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyPetsController {

	private final OwnerActivityQueryService activity;

	public MyPetsController(OwnerActivityQueryService activity) {
		this.activity = activity;
	}

	@GetMapping("/my/pets")
	String pets(Principal principal, Model model) {
		model.addAttribute("owner", this.activity.petsFor(principal));
		return "my/pets";
	}

}
