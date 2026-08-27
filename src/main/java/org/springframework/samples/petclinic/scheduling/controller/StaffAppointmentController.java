package org.springframework.samples.petclinic.scheduling.controller;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.PetRepository;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDirectBookingForm;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentRescheduleForm;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.service.AppointmentService;
import org.springframework.samples.petclinic.scheduling.service.ClinicSettingsService;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.samples.petclinic.vet.SpecialtyRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffAppointmentController {

	private final AppointmentService appointmentService;

	private final OwnerRepository ownerRepository;

	private final PetRepository petRepository;

	private final VetRepository vetRepository;

	private final SpecialtyRepository specialtyRepository;

	private final ClinicSettingsService clinicSettingsService;

	private final SchedulingTimeService timeService;

	public StaffAppointmentController(AppointmentService appointmentService, OwnerRepository ownerRepository,
			PetRepository petRepository, VetRepository vetRepository, SpecialtyRepository specialtyRepository,
			ClinicSettingsService clinicSettingsService, SchedulingTimeService timeService) {
		this.appointmentService = appointmentService;
		this.ownerRepository = ownerRepository;
		this.petRepository = petRepository;
		this.vetRepository = vetRepository;
		this.specialtyRepository = specialtyRepository;
		this.clinicSettingsService = clinicSettingsService;
		this.timeService = timeService;
	}

	@GetMapping("/scheduling/staff/appointments/{id}")
	public String showAppointmentDetails(@PathVariable("id") Integer id, Model model) {
		try {
			AppointmentDto appointment = appointmentService.getAppointmentDto(id);
			model.addAttribute("appointment", appointment);
			return "scheduling/staffAppointmentDetail";
		}
		catch (ResourceNotFoundException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
		}
	}

	@GetMapping("/scheduling/staff/appointments/new")
	public String showDirectBookingForm(@RequestParam(value = "ownerId", required = false) Integer ownerId,
			@RequestParam(value = "petId", required = false) Integer petId, Model model) {
		AppointmentDirectBookingForm form = new AppointmentDirectBookingForm();
		form.setOwnerId(ownerId);
		form.setPetId(petId);
		form.setDurationMinutes(30);
		form.setCareType(CareType.GENERAL);

		List<Owner> owners = ownerRepository.findAll();
		List<Vet> activeVets = vetRepository.findByActiveTrueOrderById();
		model.addAttribute("form", form);
		model.addAttribute("owners", owners);
		model.addAttribute("vets", activeVets);
		model.addAttribute("specialties", specialtyRepository.findByActiveTrueOrderByName());
		if (ownerId != null) {
			Owner selectedOwner = ownerRepository.findById(ownerId).orElse(null);
			if (selectedOwner != null) {
				model.addAttribute("pets", selectedOwner.getPets());
			}
		}
		return "scheduling/staffDirectBook";
	}

	@PostMapping("/scheduling/staff/appointments")
	public String submitDirectBooking(AppointmentDirectBookingForm form, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			Instant startTime = parseDateTime(form.getStartDateTime());
			AppointmentDto appt = appointmentService.directBookByStaff(form.getOwnerId(), form.getPetId(),
					form.getVetId(), startTime, form.getDurationMinutes(), form.getCareType(),
					form.getRequiredSpecialtyId(), form.getOfflineReason(),
					principal != null ? principal.getName() : "staff");
			redirectAttributes.addFlashAttribute("successMessage", "Appointment booked successfully.");
			return "redirect:/scheduling/staff/appointments/" + appt.getId();
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/appointments/new?ownerId="
					+ (form.getOwnerId() != null ? form.getOwnerId() : "") + "&petId="
					+ (form.getPetId() != null ? form.getPetId() : "");
		}
	}

	@GetMapping("/scheduling/staff/appointments/{id}/reschedule")
	public String showRescheduleForm(@PathVariable("id") Integer id, Model model) {
		try {
			AppointmentDto appointment = appointmentService.getAppointmentDto(id);
			AppointmentRescheduleForm form = new AppointmentRescheduleForm();
			form.setVetId(appointment.getVetId());
			form.setDurationMinutes(appointment.getDurationMinutes());

			model.addAttribute("appointment", appointment);
			model.addAttribute("form", form);
			model.addAttribute("vets", vetRepository.findByActiveTrueOrderById());
			return "scheduling/staffReschedule";
		}
		catch (ResourceNotFoundException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
		}
	}

	@PostMapping("/scheduling/staff/appointments/{id}/reschedule")
	public String submitReschedule(@PathVariable("id") Integer id, AppointmentRescheduleForm form, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			Instant startTime = parseDateTime(form.getStartDateTime());
			appointmentService.rescheduleAppointment(id, form.getVetId(), startTime, form.getDurationMinutes(),
					form.getRescheduleReason(), principal != null ? principal.getName() : "staff");
			redirectAttributes.addFlashAttribute("successMessage", "Appointment rescheduled successfully.");
			return "redirect:/scheduling/staff/appointments/" + id;
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/appointments/" + id + "/reschedule";
		}
	}

	@PostMapping("/scheduling/staff/appointments/{id}/cancel")
	public String cancelAppointment(@PathVariable("id") Integer id,
			@RequestParam("cancellationReason") String cancellationReason, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			appointmentService.cancelAppointmentByStaff(id, cancellationReason,
					principal != null ? principal.getName() : "staff");
			redirectAttributes.addFlashAttribute("successMessage", "Appointment cancelled successfully.");
			return "redirect:/scheduling/staff/appointments/" + id;
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/appointments/" + id;
		}
	}

	@PostMapping("/scheduling/staff/appointments/{id}/complete")
	public String completeAppointment(@PathVariable("id") Integer id,
			@RequestParam("completionNotes") String completionNotes, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			appointmentService.completeAppointment(id, completionNotes,
					principal != null ? principal.getName() : "staff");
			redirectAttributes.addFlashAttribute("successMessage", "Appointment marked as completed.");
			return "redirect:/scheduling/staff/appointments/" + id;
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/appointments/" + id;
		}
	}

	@PostMapping("/scheduling/staff/appointments/{id}/no-show")
	public String markNoShow(@PathVariable("id") Integer id, Principal principal,
			RedirectAttributes redirectAttributes) {
		try {
			appointmentService.markNoShow(id, principal != null ? principal.getName() : "staff");
			redirectAttributes.addFlashAttribute("successMessage", "Appointment marked as no-show.");
			return "redirect:/scheduling/staff/appointments/" + id;
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/staff/appointments/" + id;
		}
	}

	private Instant parseDateTime(String input) {
		if (input == null || input.trim().isEmpty()) {
			throw new IllegalArgumentException("Start time is required");
		}
		String trimmed = input.trim();
		try {
			if (trimmed.endsWith("Z")) {
				return Instant.parse(trimmed);
			}
			LocalDateTime ldt = LocalDateTime.parse(trimmed);
			return ldt.atZone(timeService.getClinicZoneId()).toInstant();
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("Invalid date-time format: " + input);
		}
	}

}
