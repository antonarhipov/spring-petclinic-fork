package org.springframework.samples.petclinic.scheduling.controller;

import org.springframework.samples.petclinic.scheduling.dto.DiagnosticsDto;
import org.springframework.samples.petclinic.scheduling.service.DiagnosticsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaffDiagnosticsController {

	private final DiagnosticsService diagnosticsService;

	public StaffDiagnosticsController(DiagnosticsService diagnosticsService) {
		this.diagnosticsService = diagnosticsService;
	}

	@GetMapping("/scheduling/staff/diagnostics")
	public String viewDiagnostics(Model model) {
		DiagnosticsDto diagnostics = diagnosticsService.getDiagnostics();
		model.addAttribute("diagnostics", diagnostics);
		return "scheduling/staffDiagnostics";
	}

}
