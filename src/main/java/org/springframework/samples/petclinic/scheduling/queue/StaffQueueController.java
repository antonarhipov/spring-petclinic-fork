package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffQueueController {

	private final StaffQueueQueryService query;

	private final StaffQueueRepository queue;

	private final StaffQueueService queueService;

	private final StaffInterpretationService interpretations;

	private final StaffSchedulingService scheduling;

	public StaffQueueController(StaffQueueQueryService query, StaffQueueRepository queue,
			StaffQueueService queueService, StaffInterpretationService interpretations,
			StaffSchedulingService scheduling) {
		this.query = query;
		this.queue = queue;
		this.queueService = queueService;
		this.interpretations = interpretations;
		this.scheduling = scheduling;
	}

	@GetMapping("/staff/queue")
	public String list(Model model) {
		model.addAttribute("items", this.query.activeItems());
		return "staff/queue";
	}

	@GetMapping("/staff/queue/{itemId}")
	public String detail(@PathVariable Integer itemId, Model model) {
		model.addAttribute("item", this.queue.findById(itemId).orElseThrow());
		return "staff/queueDetail";
	}

	@PostMapping("/staff/queue/{itemId}/claim")
	public String claim(@PathVariable Integer itemId, Authentication actor) {
		this.queueService.claim(itemId, actor);
		return "redirect:/staff/queue/" + itemId;
	}

	@PostMapping("/staff/queue/{itemId}/reassign")
	public String reassign(@PathVariable Integer itemId, @RequestParam(required = false) Integer accountId,
			@RequestParam String reason, Authentication actor) {
		this.queueService.reassign(itemId, accountId, reason, actor);
		return "redirect:/staff/queue/" + itemId;
	}

	@PostMapping("/staff/queue/{itemId}/interpretation")
	public String interpretation(@PathVariable Integer itemId, @RequestParam String visitReason,
			@RequestParam int duration, @RequestParam String careType, @RequestParam(required = false) String specialty,
			Authentication actor) {
		this.interpretations.complete(itemId, visitReason, duration, careType, specialty, actor);
		return "redirect:/staff/queue/" + itemId;
	}

	@PostMapping("/staff/queue/{itemId}/offers")
	public String offer(@PathVariable Integer itemId, Authentication actor) {
		this.scheduling.offer(itemId, actor);
		return "redirect:/staff/queue/" + itemId;
	}

	@PostMapping("/staff/queue/{itemId}/appointments")
	public String appointment(@PathVariable Integer itemId, @RequestParam Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startAt,
			@RequestParam int duration, @RequestParam String agreement, @RequestParam String reason,
			Authentication actor) {
		this.scheduling.book(itemId, vetId, startAt, duration, agreement, reason, actor);
		return "redirect:/staff/queue/" + itemId;
	}

}
