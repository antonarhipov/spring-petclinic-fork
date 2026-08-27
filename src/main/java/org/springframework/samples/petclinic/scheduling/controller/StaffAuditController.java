package org.springframework.samples.petclinic.scheduling.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.samples.petclinic.scheduling.dto.AuditRecordDto;
import org.springframework.samples.petclinic.scheduling.model.AuditRecord;
import org.springframework.samples.petclinic.scheduling.service.AuditService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffAuditController {

	private final AuditService auditService;

	public StaffAuditController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping({ "/scheduling/staff/audit", "/admin/audit" })
	public String viewAuditLog(@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "50") int size, Model model) {
		if (size > 200) {
			size = 200;
		}
		if (page < 0) {
			page = 0;
		}

		List<AuditRecord> records = auditService.findAll(PageRequest.of(page, size));
		List<AuditRecordDto> dtos = records.stream()
			.map(r -> new AuditRecordDto(r.getId(), r.getEventType(), r.getActorUsername(), r.getActorRole(),
					r.getTargetEntityType(), r.getTargetEntityId(), r.getReasonCode(), r.getOccurredAt(),
					r.getMetadataJson()))
			.toList();

		model.addAttribute("auditRecords", dtos);
		model.addAttribute("currentPage", page);
		model.addAttribute("pageSize", size);
		return "scheduling/staffAudit";
	}

}
