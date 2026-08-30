package org.springframework.samples.petclinic.scheduling.web.staff;

import java.time.LocalDate;

import org.springframework.samples.petclinic.scheduling.appointment.StaffCalendarQueryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffCalendarController {

	private final StaffCalendarQueryService calendar;

	public StaffCalendarController(StaffCalendarQueryService calendar) {
		this.calendar = calendar;
	}

	@GetMapping("/staff/calendar")
	public String calendar(@RequestParam(required = false) LocalDate date, Model model) {
		LocalDate day = date == null ? LocalDate.now() : date;
		model.addAttribute("date", day);
		model.addAttribute("appointments", this.calendar.appointmentsOn(day));
		return "scheduling/staff/calendar";
	}

}
