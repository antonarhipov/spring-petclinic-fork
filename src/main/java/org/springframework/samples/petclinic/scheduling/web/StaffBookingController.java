/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.appointment.StaffBookingService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controller for staff direct appointment booking (AC-92..94).
 */
@Controller
public class StaffBookingController {

	private final StaffBookingService staffBookingService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final SchedulingRequestRepository requestRepository;

	public StaffBookingController(StaffBookingService staffBookingService, OwnerRepository ownerRepository,
			VetRepository vetRepository, SchedulingRequestRepository requestRepository) {
		this.staffBookingService = staffBookingService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.requestRepository = requestRepository;
	}

	@GetMapping("/staff/appointments/new")
	public String showBookingForm(@RequestParam(name = "requestId", required = false) Integer requestId,
			@RequestParam(name = "petId", required = false) Integer petId, Model model) {
		SchedulingRequest request = null;
		if (requestId != null) {
			request = this.requestRepository.findById(requestId).orElse(null);
		}
		else if (petId != null) {
			request = this.requestRepository.findByActivePetId(petId).orElse(null);
		}

		StaffBookingForm form = new StaffBookingForm();
		if (request != null) {
			form.setRequestId(request.getId());
			form.setPetId(request.getPet().getId());
			form.setReason(request.getReasonText());
		}
		else if (petId != null) {
			form.setPetId(petId);
		}
		form.setDurationMinutes(30);

		model.addAttribute("form", form);
		model.addAttribute("request", request);
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("pets", this.ownerRepository.findAll().stream().flatMap(o -> o.getPets().stream()).toList());

		return "staff/bookingForm";
	}

	@PostMapping("/staff/appointments")
	public String createBooking(@ModelAttribute("form") StaffBookingForm form, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		if (form.getPetId() == null || form.getVetId() == null || form.getAppointmentDate() == null
				|| form.getStartTime() == null) {
			redirectAttributes.addFlashAttribute("error", "Missing required booking fields");
			return "redirect:/staff/appointments/new";
		}
		this.staffBookingService.directBook(form, "staff");
		redirectAttributes.addFlashAttribute("message", "appointmentBooked");
		return "redirect:/staff/calendar";
	}

}
