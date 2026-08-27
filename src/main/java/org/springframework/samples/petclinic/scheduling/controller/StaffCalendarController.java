package org.springframework.samples.petclinic.scheduling.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.scheduling.dto.CalendarItemDto;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.scheduling.service.StaffCalendarService;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class StaffCalendarController {

	private final StaffCalendarService staffCalendarService;

	private final VetRepository vetRepository;

	private final SchedulingTimeService timeService;

	public StaffCalendarController(StaffCalendarService staffCalendarService, VetRepository vetRepository,
			SchedulingTimeService timeService) {
		this.staffCalendarService = staffCalendarService;
		this.vetRepository = vetRepository;
		this.timeService = timeService;
	}

	@GetMapping("/scheduling/staff/calendar")
	public String showCalendar(
			@RequestParam(value = "startDate",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(value = "endDate",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(value = "vetId", required = false) Integer vetId, Model model) {

		if (startDate == null) {
			startDate = timeService.today();
		}
		if (endDate == null) {
			endDate = startDate.plusDays(7);
		}

		List<CalendarItemDto> items = staffCalendarService.getCalendarItems(startDate, endDate, vetId);
		List<Vet> vets = vetRepository.findAll().stream().filter(Vet::isActive).toList();

		model.addAttribute("calendarItems", items);
		model.addAttribute("vets", vets);
		model.addAttribute("startDate", startDate);
		model.addAttribute("endDate", endDate);
		model.addAttribute("selectedVetId", vetId);

		return "scheduling/staffCalendar";
	}

}
