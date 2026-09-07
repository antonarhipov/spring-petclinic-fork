package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffQueueController {

	@GetMapping("/staff/queue")
	String queue() {
		return "staff/queue";
	}

}
