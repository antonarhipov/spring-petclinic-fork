package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class StaffRequestController {

	private final RequestService requestService;

	private final SchedulingRequestRepository requests;

	private final StaffQueueQueryService queryService;

	public StaffRequestController(RequestService requestService, SchedulingRequestRepository requests,
			StaffQueueQueryService queryService) {
		this.requestService = requestService;
		this.requests = requests;
		this.queryService = queryService;
	}

	@PostMapping("/staff/requests")
	String create(@RequestParam int petId, @RequestParam String requestText, RedirectAttributes redirectAttributes) {
		RequestService.CreationResult result = this.requestService.createForStaff(petId, requestText);
		if (result.request() != null) {
			if (!result.created()) {
				redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.staff.request.existing");
			}
			return "redirect:/staff/requests/" + result.request().getId();
		}
		redirectAttributes.addFlashAttribute("actionErrorKey", result.messageKey());
		return "redirect:/staff/queue";
	}

	@PostMapping("/staff/requests/{id}/interpretation")
	String authorInterpretation(@PathVariable int id, @RequestParam long version,
			@ModelAttribute StaffInterpretationForm interpretationForm, HttpServletRequest request, Model model,
			RedirectAttributes redirectAttributes) {
		requireRequest(id);
		StaffInterpretationForm.ParseResult parsed = interpretationForm.parse();
		if (!parsed.errors().isEmpty()) {
			StaffQueueQueryService.RequestDetail detail = this.queryService.detail(id, resolveLocale(request))
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
			model.addAttribute("detail", detail);
			model.addAttribute("interpretationForm", interpretationForm);
			model.addAttribute("interpretationErrors", parsed.errors());
			return "staff/request-detail";
		}
		try {
			RequestService.ActionResult result = this.requestService.authorInterpretation(id, version, parsed.values());
			addMessage(result, redirectAttributes);
			if (result.messageKey() == null) {
				redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.staff.interpretation.saved");
			}
		}
		catch (IllegalRequestTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		catch (OptimisticLockingFailureException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.request.stale");
		}
		return "redirect:/staff/requests/" + id;
	}

	@PostMapping("/staff/requests/{id}/release-hold")
	String release(@PathVariable int id, @RequestParam long version, @RequestParam String reason,
			RedirectAttributes redirectAttributes) {
		if (!this.requests.existsById(id)) {
			throw new ResponseStatusException(NOT_FOUND);
		}
		try {
			addMessage(this.requestService.releaseHold(id, version, reason), redirectAttributes);
		}
		catch (IllegalRequestTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		catch (OptimisticLockingFailureException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.request.stale");
		}
		return "redirect:/staff/requests/" + id;
	}

	@PostMapping("/staff/requests/{id}/suggest")
	String suggest(@PathVariable int id, @RequestParam long version,
			@RequestParam(required = false) String veterinarianId, @RequestParam(required = false) String date,
			@RequestParam(required = false) String startTime, @RequestParam(required = false) String durationMinutes,
			@RequestParam(required = false) String reason, Principal principal, RedirectAttributes redirectAttributes) {
		requireRequest(id);
		if (!hasCompleteInterpretation(id, redirectAttributes)) {
			return "redirect:/staff/requests/" + id;
		}
		StaffSlotInput slot = parseSlot(veterinarianId, date, startTime, durationMinutes, redirectAttributes);
		if (slot == null) {
			return "redirect:/staff/requests/" + id;
		}
		try {
			RequestService.ActionResult result = this.requestService.placeStaffSuggestion(id, version,
					new SlotSuggestionPort.StaffSuggestionCommand(slot.veterinarianId(), slot.date(), slot.startTime(),
							slot.durationMinutes()),
					reason, principal.getName());
			addMessage(result, redirectAttributes);
		}
		catch (StaffSlotUnavailableException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", ex.getMessageKey());
		}
		catch (IllegalRequestTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		catch (IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.slot.invalid.overlap");
		}
		catch (OptimisticLockingFailureException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.request.stale");
		}
		return "redirect:/staff/requests/" + id;
	}

	@PostMapping("/staff/requests/{id}/book")
	String book(@PathVariable int id, @RequestParam long version, @RequestParam(required = false) String veterinarianId,
			@RequestParam(required = false) String date, @RequestParam(required = false) String startTime,
			@RequestParam(required = false) String durationMinutes, @RequestParam(required = false) String reason,
			Principal principal, RedirectAttributes redirectAttributes) {
		requireRequest(id);
		if (!hasCompleteInterpretation(id, redirectAttributes)) {
			return "redirect:/staff/requests/" + id;
		}
		StaffSlotInput slot = parseSlot(veterinarianId, date, startTime, durationMinutes, redirectAttributes);
		if (slot == null) {
			return "redirect:/staff/requests/" + id;
		}
		try {
			RequestService.ActionResult result = this.requestService.bookDirectly(id, version,
					new SlotSuggestionPort.StaffDirectBookingCommand(slot.veterinarianId(), slot.date(),
							slot.startTime(), slot.durationMinutes(), reason, principal.getName()));
			addMessage(result, redirectAttributes);
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.requiredReason");
		}
		catch (StaffSlotUnavailableException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", ex.getMessageKey());
		}
		catch (IllegalRequestTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		catch (IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.slot.invalid.overlap");
		}
		catch (OptimisticLockingFailureException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.request.stale");
		}
		return "redirect:/staff/requests/" + id;
	}

	private void requireRequest(int id) {
		if (!this.requests.existsById(id)) {
			throw new ResponseStatusException(NOT_FOUND);
		}
	}

	private boolean hasCompleteInterpretation(int id, RedirectAttributes redirectAttributes) {
		boolean complete = this.queryService.detail(id)
			.map(StaffQueueQueryService.RequestDetail::currentComplete)
			.orElse(false);
		if (!complete) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.staff.interpretation.required");
		}
		return complete;
	}

	private StaffSlotInput parseSlot(String veterinarianId, String date, String startTime, String durationMinutes,
			RedirectAttributes redirectAttributes) {
		try {
			int parsedVeterinarianId = Integer.parseInt(veterinarianId);
			int parsedDuration = Integer.parseInt(durationMinutes);
			if (parsedVeterinarianId <= 0 || parsedDuration <= 0) {
				throw new IllegalArgumentException("Positive values required");
			}
			return new StaffSlotInput(parsedVeterinarianId, LocalDate.parse(date), LocalTime.parse(startTime),
					parsedDuration);
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.slot.invalid.input");
			return null;
		}
	}

	private Locale resolveLocale(HttpServletRequest request) {
		if (request.getParameter("lang") != null) {
			return RequestContextUtils.getLocale(request);
		}
		Locale requestLocale = request.getLocale();
		if (requestLocale != null && !Locale.ENGLISH.getLanguage().equalsIgnoreCase(requestLocale.getLanguage())) {
			return requestLocale;
		}
		return RequestContextUtils.getLocale(request);
	}

	private void addMessage(RequestService.ActionResult result, RedirectAttributes redirectAttributes) {
		if (result.messageKey() != null) {
			redirectAttributes.addFlashAttribute("actionErrorKey", result.messageKey());
		}
	}

	private record StaffSlotInput(int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes) {
	}

}
