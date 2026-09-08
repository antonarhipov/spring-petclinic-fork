package org.springframework.samples.petclinic.scheduling.appointment;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.scheduling.request.StaffSlotUnavailableException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class StaffAppointmentController {

	private final StaffCalendarQueryService queries;

	private final StaffCalendarService calendar;

	private final Clock clock;

	public StaffAppointmentController(StaffCalendarQueryService queries, StaffCalendarService calendar, Clock clock) {
		this.queries = queries;
		this.calendar = calendar;
		this.clock = clock;
	}

	@GetMapping("/staff/calendar")
	String calendar(@RequestParam(required = false) LocalDate date,
			@RequestParam(required = false) Integer veterinarianId, @RequestParam(required = false) LocalTime startTime,
			Model model) {
		LocalDate selectedDate = date == null ? LocalDate.now(this.clock) : date;
		model.addAttribute("calendar", this.queries.calendar(selectedDate));
		model.addAttribute("selectedVeterinarianId", veterinarianId);
		model.addAttribute("selectedStartTime", startTime);
		return "staff/calendar";
	}

	@GetMapping("/staff/appointments/{id}")
	String detail(@PathVariable int id, Model model) {
		model.addAttribute("appointment",
				this.queries.detail(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
		return "staff/appointment-detail";
	}

	@PostMapping("/staff/calendar/book")
	String book(@RequestParam int petId, @RequestParam int veterinarianId, @RequestParam LocalDate date,
			@RequestParam LocalTime startTime, @RequestParam int durationMinutes, @RequestParam String reason,
			Principal principal, RedirectAttributes redirectAttributes) {
		try {
			Appointment appointment = this.calendar.book(petId, veterinarianId, date, startTime, durationMinutes,
					reason, principal.getName());
			redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.appointment.booked");
			return "redirect:/staff/appointments/" + appointment.getId();
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.requiredReason");
		}
		catch (StaffSlotUnavailableException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", ex.getMessageKey());
		}
		return "redirect:/staff/calendar?date=" + date;
	}

	@PostMapping("/staff/appointments/{id}/reschedule")
	String reschedule(@PathVariable int id, @RequestParam int veterinarianId, @RequestParam LocalDate date,
			@RequestParam LocalTime startTime, @RequestParam String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			StaffCalendarService.ActionResult result = this.calendar.reschedule(id, veterinarianId, date, startTime,
					reason, principal.getName());
			redirectAttributes.addFlashAttribute("actionNoticeKey", result.specialtyMismatch()
					? "scheduling.slot.specialty.warning" : "scheduling.appointment.rescheduled");
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.requiredReason");
		}
		catch (StaffSlotUnavailableException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", ex.getMessageKey());
		}
		catch (IllegalAppointmentTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		return "redirect:/staff/appointments/" + id;
	}

	@PostMapping("/staff/appointments/{id}/cancel")
	String cancel(@PathVariable int id, @RequestParam String reason, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			this.calendar.cancel(id, reason, principal.getName());
			redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.appointment.cancelled.staff");
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.requiredReason");
		}
		catch (IllegalAppointmentTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		return "redirect:/staff/appointments/" + id;
	}

	@PostMapping("/staff/appointments/{id}/complete")
	String complete(@PathVariable int id, @RequestParam String description, RedirectAttributes redirectAttributes) {
		try {
			this.calendar.complete(id, description);
			redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.appointment.completed");
		}
		catch (IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.validation.required");
		}
		catch (IllegalAppointmentTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		return "redirect:/staff/appointments/" + id;
	}

	@PostMapping("/staff/appointments/{id}/no-show")
	String noShow(@PathVariable int id, RedirectAttributes redirectAttributes) {
		try {
			this.calendar.markNoShow(id);
			redirectAttributes.addFlashAttribute("actionNoticeKey", "scheduling.appointment.noShow.recorded");
		}
		catch (IllegalAppointmentTransitionException ex) {
			redirectAttributes.addFlashAttribute("actionErrorKey", "scheduling.action.wrongState");
		}
		return "redirect:/staff/appointments/" + id;
	}

}
