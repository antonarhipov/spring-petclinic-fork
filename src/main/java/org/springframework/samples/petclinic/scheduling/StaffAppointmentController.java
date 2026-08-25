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

package org.springframework.samples.petclinic.scheduling;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
@RequestMapping("/staff/appointments")
public class StaffAppointmentController {

	private final BookingService bookingService;

	private final OwnerRepository owners;

	private final VetRepository vets;

	public StaffAppointmentController(BookingService bookingService, OwnerRepository owners, VetRepository vets) {
		this.bookingService = bookingService;
		this.owners = owners;
		this.vets = vets;
	}

	@GetMapping
	public String listAppointments(@RequestParam(value = "status", required = false) AppointmentStatus status,
			Model model) {
		List<Appointment> appointmentList = (status != null) ? this.bookingService.getAppointmentsByStatus(status)
				: this.bookingService.getAllAppointments();
		model.addAttribute("appointments", appointmentList);
		model.addAttribute("selectedStatus", status);
		return "scheduling/staffAppointmentsList";
	}

	@GetMapping("/new")
	public String initDirectBookingForm(Model model) {
		List<Owner> ownerList = this.owners.findAll();
		Collection<Vet> vetList = this.vets.findAll();
		model.addAttribute("owners", ownerList);
		model.addAttribute("vets", vetList);
		model.addAttribute("defaultDate", LocalDate.now().plusDays(1));
		model.addAttribute("defaultTime", LocalTime.of(9, 0));
		return "scheduling/staffDirectBookingForm";
	}

	@PostMapping("/new")
	public String processDirectBookingForm(@RequestParam("ownerId") Integer ownerId,
			@RequestParam("petId") Integer petId, @RequestParam("vetId") Integer vetId,
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam(value = "durationMinutes", defaultValue = "30") int durationMinutes,
			@RequestParam(value = "reason", required = false) String reason, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			LocalDateTime start = LocalDateTime.of(date, startTime);
			LocalDateTime end = start.plusMinutes(durationMinutes);
			Appointment appt = this.bookingService.bookDirect(ownerId, petId, vetId, start, end, reason);
			redirectAttributes.addFlashAttribute("message", "Appointment successfully booked.");
			return "redirect:/staff/appointments/" + appt.getId();
		}
		catch (Exception ex) {
			model.addAttribute("error", ex.getMessage());
			model.addAttribute("owners", this.owners.findAll());
			model.addAttribute("vets", this.vets.findAll());
			model.addAttribute("selectedOwnerId", ownerId);
			model.addAttribute("selectedPetId", petId);
			model.addAttribute("selectedVetId", vetId);
			model.addAttribute("date", date);
			model.addAttribute("startTime", startTime);
			model.addAttribute("durationMinutes", durationMinutes);
			model.addAttribute("reason", reason);
			return "scheduling/staffDirectBookingForm";
		}
	}

	@GetMapping("/{appointmentId}")
	public String showAppointmentDetails(@PathVariable("appointmentId") Integer appointmentId, Model model) {
		Appointment appointment = this.bookingService.getAppointment(appointmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
		model.addAttribute("appointment", appointment);
		return "scheduling/staffAppointmentDetails";
	}

	@GetMapping("/{appointmentId}/reschedule")
	public String initRescheduleForm(@PathVariable("appointmentId") Integer appointmentId, Model model) {
		Appointment appointment = this.bookingService.getAppointment(appointmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
		Collection<Vet> vetList = this.vets.findAll();
		model.addAttribute("appointment", appointment);
		model.addAttribute("vets", vetList);
		model.addAttribute("defaultDate", appointment.getStartTime().toLocalDate());
		model.addAttribute("defaultTime", appointment.getStartTime().toLocalTime());
		return "scheduling/staffRescheduleForm";
	}

	@PostMapping("/{appointmentId}/reschedule")
	public String processRescheduleForm(@PathVariable("appointmentId") Integer appointmentId,
			@RequestParam("vetId") Integer vetId,
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam(value = "durationMinutes", defaultValue = "30") int durationMinutes,
			@RequestParam("changeReason") String changeReason, Model model, RedirectAttributes redirectAttributes) {
		try {
			LocalDateTime start = LocalDateTime.of(date, startTime);
			LocalDateTime end = start.plusMinutes(durationMinutes);
			this.bookingService.rescheduleAppointment(appointmentId, vetId, start, end, changeReason);
			redirectAttributes.addFlashAttribute("message", "Appointment successfully rescheduled.");
			return "redirect:/staff/appointments/" + appointmentId;
		}
		catch (Exception ex) {
			Appointment appointment = this.bookingService.getAppointment(appointmentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
			model.addAttribute("appointment", appointment);
			model.addAttribute("vets", this.vets.findAll());
			model.addAttribute("error", ex.getMessage());
			model.addAttribute("selectedVetId", vetId);
			model.addAttribute("date", date);
			model.addAttribute("startTime", startTime);
			model.addAttribute("durationMinutes", durationMinutes);
			model.addAttribute("changeReason", changeReason);
			return "scheduling/staffRescheduleForm";
		}
	}

	@PostMapping("/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable("appointmentId") Integer appointmentId,
			@RequestParam("changeReason") String changeReason, RedirectAttributes redirectAttributes) {
		try {
			this.bookingService.cancelByStaff(appointmentId, changeReason);
			redirectAttributes.addFlashAttribute("message", "Appointment cancelled successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/{appointmentId}/complete")
	public String completeAppointment(@PathVariable("appointmentId") Integer appointmentId,
			RedirectAttributes redirectAttributes) {
		try {
			this.bookingService.completeAppointment(appointmentId);
			redirectAttributes.addFlashAttribute("message", "Appointment marked as completed. Visit created.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/appointments/" + appointmentId;
	}

	@PostMapping("/{appointmentId}/no-show")
	public String markNoShow(@PathVariable("appointmentId") Integer appointmentId,
			RedirectAttributes redirectAttributes) {
		try {
			this.bookingService.markNoShow(appointmentId);
			redirectAttributes.addFlashAttribute("message", "Appointment marked as No-Show.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/staff/appointments/" + appointmentId;
	}

}
