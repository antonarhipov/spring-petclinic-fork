package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.offer.AppointmentOfferRepository;
import org.springframework.samples.petclinic.scheduling.offer.OfferState;

@Controller
public class StaffCalendarController {

	private final AppointmentRepository appointments;

	private final AppointmentOfferRepository offers;

	private final ClinicSchedulingSettingsService settings;

	private final VeterinarianAvailabilityService availability;

	public StaffCalendarController(AppointmentRepository appointments, AppointmentOfferRepository offers,
			ClinicSchedulingSettingsService settings, VeterinarianAvailabilityService availability) {
		this.appointments = appointments;
		this.offers = offers;
		this.settings = settings;
		this.availability = availability;
	}

	@GetMapping("/staff/calendar")
	public String calendar(Model model) {
		model.addAttribute("appointments", this.appointments.findAll());
		model.addAttribute("holds", this.offers.findByState(OfferState.HELD));
		return "staff/calendar";
	}

	@GetMapping("/staff/settings/scheduling")
	public String settings(Model model) {
		model.addAttribute("settings", this.settings.get());
		return "staff/schedulingSettings";
	}

	@PostMapping("/staff/settings/scheduling")
	public String updateSettings(@RequestParam String zone, @RequestParam int horizon, @RequestParam int notice,
			@RequestParam int hold, @RequestParam String guidance, Authentication actor) {
		this.settings.update(zone, horizon, notice, hold, guidance, actor);
		return "redirect:/staff/settings/scheduling";
	}

	@PostMapping("/staff/veterinarians/{vetId}/availability/shifts")
	public String shift(@PathVariable Integer vetId, @RequestParam int day,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end, Authentication actor) {
		this.availability.addShift(vetId, day, start, end, actor);
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/veterinarians/{vetId}/availability/leave")
	public String leave(@PathVariable Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
			@RequestParam(required = false) String reason, Authentication actor) {
		this.availability.addLeave(vetId, start, end, reason, actor);
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/veterinarians/{vetId}/availability/exceptions")
	public String exception(@PathVariable Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end, @RequestParam boolean available,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime windowStart,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime windowEnd,
			@RequestParam(required = false) String reason, Authentication actor) {
		this.availability.addException(vetId, start, end, available, windowStart, windowEnd, reason, actor);
		return "redirect:/staff/calendar";
	}

	@PostMapping("/staff/closures")
	public String closure(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
			@RequestParam(required = false) String reason, Authentication actor) {
		this.availability.addClosure(start, end, reason, actor);
		return "redirect:/staff/calendar";
	}

}
