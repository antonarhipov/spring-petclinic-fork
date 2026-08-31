package org.springframework.samples.petclinic.availability;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff/calendar")
public class StaffCalendarController {

	private final StaffCalendarQueryService staffCalendarQueryService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final VetRepository vetRepository;

	private final Clock clock;

	public StaffCalendarController(StaffCalendarQueryService staffCalendarQueryService,
			EffectiveAvailabilityService effectiveAvailabilityService, VetRepository vetRepository, Clock clock) {
		this.staffCalendarQueryService = staffCalendarQueryService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@GetMapping({ "", "/week" })
	public String weekView(
			@RequestParam(name = "date",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "vetId", required = false) Integer vetId, Model model) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate targetDate = (date != null) ? date : LocalDate.now(this.clock.withZone(zoneId));

		StaffCalendarQueryService.WeekCalendarView weekView = this.staffCalendarQueryService.getWeekView(targetDate,
				vetId);
		model.addAttribute("weekView", weekView);
		model.addAttribute("currentDate", targetDate);
		model.addAttribute("filterVetId", vetId);
		model.addAttribute("allVets", this.vetRepository.findAll());
		return "staff/calendar/week";
	}

	@GetMapping("/table")
	public String tableView(
			@RequestParam(name = "startDate",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(name = "endDate",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(name = "vetId", required = false) Integer vetId, Model model) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		LocalDate start = (startDate != null) ? startDate : LocalDate.now(this.clock.withZone(zoneId));
		LocalDate end = (endDate != null) ? endDate : start.plusDays(14);

		List<StaffCalendarQueryService.CalendarAppointmentItem> items = this.staffCalendarQueryService
			.getTableAppointments(start, end, vetId);
		model.addAttribute("appointments", items);
		model.addAttribute("startDate", start);
		model.addAttribute("endDate", end);
		model.addAttribute("filterVetId", vetId);
		model.addAttribute("allVets", this.vetRepository.findAll());
		model.addAttribute("weekView", this.staffCalendarQueryService.getWeekView(start, vetId));
		return "staff/calendar/table";
	}

}
