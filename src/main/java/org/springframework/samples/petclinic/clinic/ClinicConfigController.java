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

package org.springframework.samples.petclinic.clinic;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetDateException;
import org.springframework.samples.petclinic.vet.VetDateExceptionRepository;
import org.springframework.samples.petclinic.vet.VetDateExceptionType;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.samples.petclinic.vet.VetWeeklyShift;
import org.springframework.samples.petclinic.vet.VetWeeklyShiftRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff/clinic")
@PreAuthorize("hasRole('STAFF')")
public class ClinicConfigController {

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final PartOfDayRepository partOfDayRepository;

	private final VetRepository vetRepository;

	private final VetWeeklyShiftRepository vetWeeklyShiftRepository;

	private final VetDateExceptionRepository vetDateExceptionRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	public ClinicConfigController(ClinicSettingsRepository clinicSettingsRepository,
			PartOfDayRepository partOfDayRepository, VetRepository vetRepository,
			VetWeeklyShiftRepository vetWeeklyShiftRepository, VetDateExceptionRepository vetDateExceptionRepository,
			ClinicClosureRepository clinicClosureRepository) {
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.partOfDayRepository = partOfDayRepository;
		this.vetRepository = vetRepository;
		this.vetWeeklyShiftRepository = vetWeeklyShiftRepository;
		this.vetDateExceptionRepository = vetDateExceptionRepository;
		this.clinicClosureRepository = clinicClosureRepository;
	}

	@GetMapping
	public String index() {
		return "redirect:/staff/clinic/settings";
	}

	// 1. Clinic Settings
	@GetMapping("/settings")
	public String showSettings(Model model) {
		ClinicSettings settings = this.clinicSettingsRepository.getSettingsOrDefault();
		model.addAttribute("settings", settings);
		return "clinic/settings";
	}

	@PostMapping("/settings")
	public String updateSettings(@RequestParam("timeZone") String timeZone,
			@RequestParam("minVisitMinutes") int minVisitMinutes, @RequestParam("maxVisitMinutes") int maxVisitMinutes,
			@RequestParam("defaultVisitMinutes") int defaultVisitMinutes,
			@RequestParam("bookingHorizonDays") int bookingHorizonDays,
			@RequestParam("holdDurationMinutes") int holdDurationMinutes,
			@RequestParam(value = "gridMinutes", defaultValue = "15") int gridMinutes) {
		ClinicSettings settings = this.clinicSettingsRepository.getSettingsOrDefault();
		settings.setTimeZone(timeZone);
		settings.setMinVisitMinutes(minVisitMinutes);
		settings.setMaxVisitMinutes(maxVisitMinutes);
		settings.setDefaultVisitMinutes(defaultVisitMinutes);
		settings.setBookingHorizonDays(bookingHorizonDays);
		settings.setHoldDurationMinutes(holdDurationMinutes);
		settings.setGridMinutes(gridMinutes);
		this.clinicSettingsRepository.save(settings);
		return "redirect:/staff/clinic/settings?success";
	}

	// 2. Parts of Day
	@GetMapping("/parts-of-day")
	public String listPartsOfDay(Model model) {
		List<PartOfDay> parts = this.partOfDayRepository.findAll();
		model.addAttribute("partsOfDay", parts);
		return "clinic/partsOfDay";
	}

	@PostMapping("/parts-of-day/new")
	public String createPartOfDay(@RequestParam("name") String name, @RequestParam("startTime") String startTime,
			@RequestParam("endTime") String endTime) {
		PartOfDay partOfDay = new PartOfDay(name.trim().toUpperCase(), LocalTime.parse(startTime),
				LocalTime.parse(endTime));
		this.partOfDayRepository.save(partOfDay);
		return "redirect:/staff/clinic/parts-of-day";
	}

	@PostMapping("/parts-of-day/{id}/delete")
	public String deletePartOfDay(@PathVariable("id") int id) {
		this.partOfDayRepository.deleteById(id);
		return "redirect:/staff/clinic/parts-of-day";
	}

	// 3. Vet Weekly Shifts
	@GetMapping("/vets/{vetId}/shifts")
	public String showVetShifts(@PathVariable("vetId") int vetId, Model model) {
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		List<Vet> vets = new java.util.ArrayList<>(this.vetRepository.findAll());
		List<VetWeeklyShift> shifts = this.vetWeeklyShiftRepository.findByVetId(vetId);

		model.addAttribute("vet", vet);
		model.addAttribute("vets", vets);
		model.addAttribute("shifts", shifts);
		model.addAttribute("daysOfWeek", DayOfWeek.values());
		return "clinic/vetShifts";
	}

	@PostMapping("/vets/{vetId}/shifts/new")
	public String createVetShift(@PathVariable("vetId") int vetId, @RequestParam("dayOfWeek") String dayOfWeek,
			@RequestParam("startTime") String startTime, @RequestParam("endTime") String endTime) {
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		VetWeeklyShift shift = new VetWeeklyShift(vet, DayOfWeek.valueOf(dayOfWeek.toUpperCase()),
				LocalTime.parse(startTime), LocalTime.parse(endTime));
		this.vetWeeklyShiftRepository.save(shift);
		return "redirect:/staff/clinic/vets/" + vetId + "/shifts";
	}

	@PostMapping("/vets/{vetId}/shifts/{shiftId}/delete")
	public String deleteVetShift(@PathVariable("vetId") int vetId, @PathVariable("shiftId") int shiftId) {
		this.vetWeeklyShiftRepository.deleteById(shiftId);
		return "redirect:/staff/clinic/vets/" + vetId + "/shifts";
	}

	// 4. Vet Date Exceptions
	@GetMapping("/vets/{vetId}/exceptions")
	public String showVetExceptions(@PathVariable("vetId") int vetId, Model model) {
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		List<Vet> vets = new java.util.ArrayList<>(this.vetRepository.findAll());
		List<VetDateException> exceptions = this.vetDateExceptionRepository.findByVetId(vetId);

		model.addAttribute("vet", vet);
		model.addAttribute("vets", vets);
		model.addAttribute("exceptions", exceptions);
		model.addAttribute("exceptionTypes", VetDateExceptionType.values());
		return "clinic/vetExceptions";
	}

	@PostMapping("/vets/{vetId}/exceptions/new")
	public String createVetException(@PathVariable("vetId") int vetId, @RequestParam("startDate") String startDate,
			@RequestParam("endDate") String endDate, @RequestParam("type") String type,
			@RequestParam(value = "startTime", required = false) String startTime,
			@RequestParam(value = "endTime", required = false) String endTime,
			@RequestParam(value = "reason", required = false) String reason) {
		Vet vet = this.vetRepository.findById(vetId).orElseThrow();
		LocalTime start = (startTime != null && !startTime.isBlank()) ? LocalTime.parse(startTime) : null;
		LocalTime end = (endTime != null && !endTime.isBlank()) ? LocalTime.parse(endTime) : null;

		VetDateException exception = new VetDateException(vet, LocalDate.parse(startDate), LocalDate.parse(endDate),
				VetDateExceptionType.valueOf(type.toUpperCase()), start, end, reason);
		this.vetDateExceptionRepository.save(exception);
		return "redirect:/staff/clinic/vets/" + vetId + "/exceptions";
	}

	@PostMapping("/vets/{vetId}/exceptions/{exceptionId}/delete")
	public String deleteVetException(@PathVariable("vetId") int vetId, @PathVariable("exceptionId") int exceptionId) {
		this.vetDateExceptionRepository.deleteById(exceptionId);
		return "redirect:/staff/clinic/vets/" + vetId + "/exceptions";
	}

	// 5. Clinic Closures
	@GetMapping("/closures")
	public String listClosures(Model model) {
		List<ClinicClosure> closures = this.clinicClosureRepository.findAll();
		model.addAttribute("closures", closures);
		return "clinic/closures";
	}

	@PostMapping("/closures/new")
	public String createClosure(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate,
			@RequestParam(value = "reason", required = false) String reason,
			@RequestParam(value = "startTime", required = false) String startTime,
			@RequestParam(value = "endTime", required = false) String endTime) {
		LocalTime start = (startTime != null && !startTime.isBlank()) ? LocalTime.parse(startTime) : null;
		LocalTime end = (endTime != null && !endTime.isBlank()) ? LocalTime.parse(endTime) : null;

		ClinicClosure closure = new ClinicClosure(LocalDate.parse(startDate), LocalDate.parse(endDate), reason, start,
				end);
		this.clinicClosureRepository.save(closure);
		return "redirect:/staff/clinic/closures";
	}

	@PostMapping("/closures/{id}/delete")
	public String deleteClosure(@PathVariable("id") int id) {
		this.clinicClosureRepository.deleteById(id);
		return "redirect:/staff/clinic/closures";
	}

}
