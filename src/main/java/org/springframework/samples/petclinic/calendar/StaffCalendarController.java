/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.calendar;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffCalendarController {

	private final VetRepository vetRepository;

	private final VetWeeklyShiftRepository shiftRepository;

	private final VetAvailabilityExceptionRepository exceptionRepository;

	private final VetLeaveRepository leaveRepository;

	private final ClinicClosureRepository closureRepository;

	public StaffCalendarController(VetRepository vetRepository, VetWeeklyShiftRepository shiftRepository,
			VetAvailabilityExceptionRepository exceptionRepository, VetLeaveRepository leaveRepository,
			ClinicClosureRepository closureRepository) {
		this.vetRepository = vetRepository;
		this.shiftRepository = shiftRepository;
		this.exceptionRepository = exceptionRepository;
		this.leaveRepository = leaveRepository;
		this.closureRepository = closureRepository;
	}

	@GetMapping("/staff/calendar")
	public String index(Model model) {
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("closures", this.closureRepository.findAll());
		return "calendar/index";
	}

	@GetMapping("/staff/calendar/vets/{vetId}")
	public String vetSchedule(@PathVariable Integer vetId, Model model) {
		Vet vet = loadVet(vetId);
		model.addAttribute("vet", vet);
		model.addAttribute("shifts", this.shiftRepository.findByVetId(vetId));
		model.addAttribute("exceptions", this.exceptionRepository.findByVetId(vetId));
		model.addAttribute("leaves", this.leaveRepository.findByVetId(vetId));
		model.addAttribute("exceptionTypes", ExceptionType.values());
		return "calendar/vetSchedule";
	}

	@PostMapping("/staff/calendar/vets/{vetId}/shifts")
	public String addShift(@PathVariable Integer vetId, @RequestParam int dayOfWeek,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startLocal,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endLocal,
			RedirectAttributes redirectAttributes) {
		Vet vet = loadVet(vetId);
		this.shiftRepository.save(new VetWeeklyShift(vet, dayOfWeek, startLocal, endLocal));
		redirectAttributes.addFlashAttribute("message", "Shift added");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@PostMapping("/staff/calendar/vets/{vetId}/shifts/{shiftId}/delete")
	public String deleteShift(@PathVariable Integer vetId, @PathVariable Integer shiftId,
			RedirectAttributes redirectAttributes) {
		this.shiftRepository.deleteById(shiftId);
		redirectAttributes.addFlashAttribute("message", "Shift deleted");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@PostMapping("/staff/calendar/vets/{vetId}/exceptions")
	public String addException(@PathVariable Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam ExceptionType type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startLocal,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endLocal,
			RedirectAttributes redirectAttributes) {
		Vet vet = loadVet(vetId);
		this.exceptionRepository.save(new VetAvailabilityException(vet, date, type, startLocal, endLocal));
		redirectAttributes.addFlashAttribute("message", "Exception added");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@PostMapping("/staff/calendar/vets/{vetId}/exceptions/{id}/delete")
	public String deleteException(@PathVariable Integer vetId, @PathVariable Integer id,
			RedirectAttributes redirectAttributes) {
		this.exceptionRepository.deleteById(id);
		redirectAttributes.addFlashAttribute("message", "Exception deleted");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@PostMapping("/staff/calendar/vets/{vetId}/leave")
	public String addLeave(@PathVariable Integer vetId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			RedirectAttributes redirectAttributes) {
		Vet vet = loadVet(vetId);
		this.leaveRepository.save(new VetLeave(vet, fromDate, toDate));
		redirectAttributes.addFlashAttribute("message", "Leave added");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@PostMapping("/staff/calendar/vets/{vetId}/leave/{id}/delete")
	public String deleteLeave(@PathVariable Integer vetId, @PathVariable Integer id,
			RedirectAttributes redirectAttributes) {
		this.leaveRepository.deleteById(id);
		redirectAttributes.addFlashAttribute("message", "Leave deleted");
		return "redirect:/staff/calendar/vets/" + vetId;
	}

	@GetMapping("/staff/calendar/closures")
	public String closures(Model model) {
		model.addAttribute("closures", this.closureRepository.findAll());
		return "calendar/closures";
	}

	@PostMapping("/staff/calendar/closures")
	public String addClosure(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
			RedirectAttributes redirectAttributes) {
		this.closureRepository.save(new ClinicClosure(fromDate, toDate));
		redirectAttributes.addFlashAttribute("message", "Closure added");
		return "redirect:/staff/calendar/closures";
	}

	@PostMapping("/staff/calendar/closures/{id}/delete")
	public String deleteClosure(@PathVariable Integer id, RedirectAttributes redirectAttributes) {
		this.closureRepository.deleteById(id);
		redirectAttributes.addFlashAttribute("message", "Closure deleted");
		return "redirect:/staff/calendar/closures";
	}

	private Vet loadVet(Integer vetId) {
		return this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid vet id: " + vetId));
	}

}
