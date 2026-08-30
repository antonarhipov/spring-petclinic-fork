package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.Instant;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.appointment.BookingAuthorization;
import org.springframework.samples.petclinic.scheduling.interpretation.ManualInterpretationService;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.queue.StaffAssistedSchedulingService;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueItem;
import org.springframework.samples.petclinic.scheduling.queue.StaffQueueService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffQueueController {

	private final StaffQueueService queue;

	private final StaffAssistedSchedulingService assisted;

	private final ManualInterpretationService manualInterpretation;

	private final SchedulingRequestRepository requests;

	private final AccountRepository accounts;

	public StaffQueueController(StaffQueueService queue, StaffAssistedSchedulingService assisted,
			ManualInterpretationService manualInterpretation, SchedulingRequestRepository requests,
			AccountRepository accounts) {
		this.queue = queue;
		this.assisted = assisted;
		this.manualInterpretation = manualInterpretation;
		this.requests = requests;
		this.accounts = accounts;
	}

	@GetMapping("/staff/queue")
	public String list(Model model) {
		model.addAttribute("items", this.queue.openQueue());
		return "scheduling/staff/queue";
	}

	@GetMapping("/staff/queue/{id}")
	public String detail(@PathVariable Long id, Model model) {
		StaffQueueItem item = this.queue.require(id);
		model.addAttribute("item", item);
		model.addAttribute("notes", this.queue.notes(id));
		model.addAttribute("request", this.requests.findById(item.getRequestId()).orElseThrow());
		return "scheduling/staff/queue-item";
	}

	@PostMapping("/staff/queue/{id}/claim")
	public String claim(@PathVariable Long id, @RequestParam Integer expectedVersion, Authentication authentication) {
		this.queue.claim(id, staff(authentication).getId(), expectedVersion);
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/unclaim")
	public String unclaim(@PathVariable Long id, @RequestParam Integer expectedVersion, @RequestParam String reason,
			Authentication authentication) {
		this.queue.unclaim(id, staff(authentication).getId(), expectedVersion, reason);
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/reassign")
	public String reassign(@PathVariable Long id, @RequestParam Integer expectedVersion, @RequestParam Long assigneeId,
			@RequestParam String reason, Authentication authentication) {
		this.queue.reassign(id, staff(authentication).getId(), assigneeId, expectedVersion, reason);
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/notes")
	public String note(@PathVariable Long id, @RequestParam String body, Authentication authentication) {
		this.queue.addNote(id, staff(authentication).getId(), body);
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/interpretation")
	public String interpretation(@PathVariable Long id, @RequestParam String visitReason,
			@RequestParam int durationMinutes, @RequestParam String careType, @RequestParam String urgency,
			Authentication authentication) {
		StaffQueueItem item = this.queue.require(id);
		this.manualInterpretation.save(item.getRequestId(), staff(authentication).getId(), visitReason, durationMinutes,
				careType, urgency);
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/hold")
	public String hold(@PathVariable Long id, @RequestParam int veterinarianId, @RequestParam Instant startAt,
			@RequestParam Instant endAt, Authentication authentication) {
		StaffQueueItem item = this.queue.require(id);
		this.assisted.holdExactSlot(item.getRequestId(), staff(authentication).getId(),
				new CandidateSlot(veterinarianId + "@" + startAt, veterinarianId, startAt, endAt, "STAFF", 0));
		return "redirect:/staff/queue/" + id;
	}

	@PostMapping("/staff/queue/{id}/book")
	public String book(@PathVariable Long id, @RequestParam int veterinarianId, @RequestParam Instant startAt,
			@RequestParam Instant endAt, @RequestParam String authorizationBasis, @RequestParam Instant agreementAt,
			@RequestParam String agreementMethod, @RequestParam(required = false) Integer supportingVisitId,
			@RequestParam(required = false) String staffReason, Authentication authentication) {
		StaffQueueItem item = this.queue.require(id);
		Account staff = staff(authentication);
		this.assisted.book(item.getRequestId(), staff.getId(),
				new CandidateSlot(veterinarianId + "@" + startAt, veterinarianId, startAt, endAt, "STAFF", 0),
				new BookingAuthorization(authorizationBasis, staff.getId(), agreementAt, agreementMethod,
						supportingVisitId),
				staffReason);
		return "redirect:/staff/queue";
	}

	@PostMapping("/staff/queue/{id}/close")
	public String close(@PathVariable Long id, @RequestParam Integer expectedVersion, @RequestParam String resolution,
			Authentication authentication) {
		this.queue.close(id, staff(authentication).getId(), expectedVersion, resolution);
		return "redirect:/staff/queue";
	}

	private Account staff(Authentication authentication) {
		return this.accounts.findByUsername(authentication.getName()).orElseThrow();
	}

}
