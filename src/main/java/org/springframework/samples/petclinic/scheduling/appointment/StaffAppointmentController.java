package org.springframework.samples.petclinic.scheduling.appointment;

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
public class StaffAppointmentController {

	private final AppointmentService appointments;

	private final AppointmentLifecycleService lifecycle;

	private final AppointmentRepository repository;

	public StaffAppointmentController(AppointmentService appointments, AppointmentLifecycleService lifecycle,
			AppointmentRepository repository) {
		this.appointments = appointments;
		this.lifecycle = lifecycle;
		this.repository = repository;
	}

	@GetMapping("/staff/appointments/{appointmentId}")
	public String details(@PathVariable Integer appointmentId, Model model) {
		model.addAttribute("appointment", this.repository.findById(appointmentId).orElseThrow());
		return "staff/appointment";
	}

	@PostMapping("/staff/appointments")
	public String book(@RequestParam Integer petId, @RequestParam Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startAt,
			@RequestParam int duration, @RequestParam(defaultValue = "STAFF_OPERATIONAL") AppointmentSource source,
			@RequestParam(required = false) String agreement, @RequestParam String reason, Authentication actor) {
		this.appointments.directBook(petId, vetId, startAt, duration, source, agreement, reason, actor);
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/appointments/{appointmentId}/reschedule")
	public String reschedule(@PathVariable Integer appointmentId, @RequestParam Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startAt,
			@RequestParam String reason, @RequestParam(required = false) String note, Authentication actor) {
		this.appointments.reschedule(appointmentId, vetId, startAt, reason, note, actor);
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/staff/appointments/{appointmentId}/cancel")
	public String cancel(@PathVariable Integer appointmentId, @RequestParam String reason,
			@RequestParam(required = false) String note, Authentication actor) {
		this.appointments.cancel(appointmentId, reason, note, actor);
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/staff/appointments/{appointmentId}/complete")
	public String complete(@PathVariable Integer appointmentId, @RequestParam(required = false) String notes,
			Authentication actor) {
		this.lifecycle.complete(appointmentId, notes, actor);
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/staff/appointments/{appointmentId}/no-show")
	public String noShow(@PathVariable Integer appointmentId, @RequestParam String reason,
			@RequestParam(required = false) String note, Authentication actor) {
		this.lifecycle.noShow(appointmentId, reason, note, actor);
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/staff/appointments/{appointmentId}/correct-status")
	public String correct(@PathVariable Integer appointmentId, @RequestParam AppointmentStatus status,
			@RequestParam String reason, @RequestParam(required = false) String notes, Authentication actor) {
		this.lifecycle.correct(appointmentId, status, reason, notes, actor);
		return "redirect:/staff/appointments/" + appointmentId;
	}

}
