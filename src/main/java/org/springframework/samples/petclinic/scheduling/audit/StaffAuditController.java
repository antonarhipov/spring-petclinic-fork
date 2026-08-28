package org.springframework.samples.petclinic.scheduling.audit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffAuditController {

	private final AuditRecordRepository records;

	public StaffAuditController(AuditRecordRepository records) {
		this.records = records;
	}

	@GetMapping("/staff/audit")
	public String audit(Model model) {
		model.addAttribute("records", this.records.findAll());
		return "staff/audit";
	}

}
