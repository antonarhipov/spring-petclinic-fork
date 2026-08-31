package org.springframework.samples.petclinic.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Staff landing route redirecting to the fallback queue.
 */
@Controller
@RequestMapping("/staff")
public class StaffPortalController {

	@GetMapping({ "", "/" })
	public String staffHome() {
		return "redirect:/staff/queue";
	}

}
