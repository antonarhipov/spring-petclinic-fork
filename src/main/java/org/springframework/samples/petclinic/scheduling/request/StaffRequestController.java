package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class StaffRequestController {

	private final RequestService requestService;

	private final SchedulingRequestRepository requests;

	public StaffRequestController(RequestService requestService, SchedulingRequestRepository requests) {
		this.requestService = requestService;
		this.requests = requests;
	}

	@PostMapping("/staff/requests/{id}/release-hold")
	String release(@PathVariable int id, @RequestParam long version, @RequestParam String reason) {
		if (!this.requests.existsById(id)) {
			throw new ResponseStatusException(NOT_FOUND);
		}
		this.requestService.releaseHold(id, version, reason);
		return "redirect:/staff/queue";
	}

	@PostMapping("/staff/requests/{id}/suggest")
	String suggest(@PathVariable int id, @RequestParam long version, @RequestParam int veterinarianId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam int durationMinutes, RedirectAttributes redirectAttributes) {
		requireRequest(id);
		RequestService.ActionResult result = this.requestService.placeStaffSuggestion(id, version,
				new SlotSuggestionPort.StaffSuggestionCommand(veterinarianId, date, startTime, durationMinutes));
		addMessage(result, redirectAttributes);
		return "redirect:/staff/queue";
	}

	@PostMapping("/staff/requests/{id}/book")
	String book(@PathVariable int id, @RequestParam long version, @RequestParam int veterinarianId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam int durationMinutes, @RequestParam String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		requireRequest(id);
		RequestService.ActionResult result = this.requestService.bookDirectly(id, version,
				new SlotSuggestionPort.StaffDirectBookingCommand(veterinarianId, date, startTime, durationMinutes,
						reason, principal.getName()));
		addMessage(result, redirectAttributes);
		return "redirect:/staff/queue";
	}

	private void requireRequest(int id) {
		if (!this.requests.existsById(id)) {
			throw new ResponseStatusException(NOT_FOUND);
		}
	}

	private void addMessage(RequestService.ActionResult result, RedirectAttributes redirectAttributes) {
		if (result.messageKey() != null) {
			redirectAttributes.addFlashAttribute("actionErrorKey", result.messageKey());
		}
	}

}
