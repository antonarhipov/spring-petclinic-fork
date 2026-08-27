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
package org.springframework.samples.petclinic.staff;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
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

/**
 * Controller providing staff direct booking workflows against the calendar grid.
 */
@Controller
@PreAuthorize("hasRole('STAFF')")
public class StaffBookingController {

	private final StaffBookingService staffBookingService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final ClinicSettingsRepository clinicSettingsRepository;

	public StaffBookingController(StaffBookingService staffBookingService, OwnerRepository ownerRepository,
			VetRepository vetRepository, ClinicSettingsRepository clinicSettingsRepository) {
		this.staffBookingService = staffBookingService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.clinicSettingsRepository = clinicSettingsRepository;
	}

	@GetMapping("/staff/appointments/new")
	public String initDirectBookingForm(@RequestParam(value = "ownerId", required = false) Integer ownerId,
			@RequestParam(value = "petId", required = false) Integer petId,
			@RequestParam(value = "vetId", required = false) Integer vetId,
			@RequestParam(value = "date",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(value = "durationMin", required = false) Integer durationMin) {
		if (ownerId != null && petId != null) {
			StringBuilder redirectUrl = new StringBuilder("redirect:/owners/").append(ownerId)
				.append("/pets/")
				.append(petId)
				.append("/appointments/new");
			boolean hasParam = false;
			if (vetId != null) {
				redirectUrl.append("?vetId=").append(vetId);
				hasParam = true;
			}
			if (date != null) {
				redirectUrl.append(hasParam ? "&" : "?").append("date=").append(date);
				hasParam = true;
			}
			if (durationMin != null) {
				redirectUrl.append(hasParam ? "&" : "?").append("durationMin=").append(durationMin);
			}
			return redirectUrl.toString();
		}
		return "redirect:/owners/find";
	}

	@GetMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String initNewAppointmentForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@RequestParam(value = "vetId", required = false) Integer vetId,
			@RequestParam(value = "date",
					required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(value = "durationMin", required = false) Integer durationMin, Model model) {

		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + ownerId);
		}

		List<Vet> vets = this.vetRepository.findAll();
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();

		Integer selectedVetId = vetId;
		if (selectedVetId == null && !vets.isEmpty()) {
			selectedVetId = vets.get(0).getId();
		}

		LocalDate today = LocalDate.now(settings.getZone());
		LocalDate selectedDate = date != null ? date : today.plusDays(1);
		int clampedDuration = settings.clampDuration(durationMin != null ? durationMin : settings.getDefaultVisitMin());

		List<SlotOption> slots = Collections.emptyList();
		if (selectedVetId != null) {
			List<Instant> availableInstants = this.staffBookingService.getAvailableSlots(selectedVetId, selectedDate,
					clampedDuration);
			slots = availableInstants.stream()
				.map(inst -> new SlotOption(inst, clampedDuration, settings.getZone()))
				.toList();
		}

		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("vets", vets);
		model.addAttribute("selectedVetId", selectedVetId);
		model.addAttribute("selectedDate", selectedDate);
		model.addAttribute("durationMin", clampedDuration);
		model.addAttribute("minDuration", settings.getMinVisitMin());
		model.addAttribute("maxDuration", settings.getMaxVisitMin());
		model.addAttribute("minDate", today);
		model.addAttribute("maxDate", today.plusDays(settings.getBookingHorizonDays() - 1));
		model.addAttribute("slots", slots);

		return "staff/bookAppointmentForm";
	}

	@PostMapping("/owners/{ownerId}/pets/{petId}/appointments/new")
	public String processNewAppointmentForm(@PathVariable("ownerId") int ownerId, @PathVariable("petId") int petId,
			@RequestParam("vetId") int vetId, @RequestParam("startInstant") String startInstantStr,
			@RequestParam(value = "durationMin", required = false) Integer durationMin,
			@RequestParam(value = "reason", required = false) String reason, RedirectAttributes redirectAttributes) {

		try {
			Instant startInstant = Instant.parse(startInstantStr);
			Appointment appointment = this.staffBookingService.bookDirectAppointment(ownerId, petId, vetId,
					startInstant, durationMin, reason);

			redirectAttributes.addFlashAttribute("message",
					"Appointment scheduled successfully for " + appointment.getPet().getName() + ".");
			return "redirect:/owners/" + ownerId;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
			return "redirect:/owners/" + ownerId + "/pets/" + petId + "/appointments/new?vetId=" + vetId;
		}
	}

}
