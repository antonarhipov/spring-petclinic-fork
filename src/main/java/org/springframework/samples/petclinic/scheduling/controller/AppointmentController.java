package org.springframework.samples.petclinic.scheduling.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.dto.AppointmentDto;
import org.springframework.samples.petclinic.scheduling.service.AppointmentService;
import org.springframework.samples.petclinic.security.Account;
import org.springframework.samples.petclinic.security.AccountRepository;
import org.springframework.samples.petclinic.system.ResourceNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AppointmentController {

	private final AppointmentService appointmentService;

	private final AccountRepository accountRepository;

	public AppointmentController(AppointmentService appointmentService, AccountRepository accountRepository) {
		this.appointmentService = appointmentService;
		this.accountRepository = accountRepository;
	}

	@GetMapping("/scheduling/appointments")
	public String listOwnerUpcomingAppointments(Principal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		List<AppointmentDto> upcomingAppointments = appointmentService.getOwnerUpcomingAppointments(owner.getId());
		model.addAttribute("appointments", upcomingAppointments);
		return "scheduling/appointments";
	}

	@GetMapping("/scheduling/appointments/{id}")
	public String showAppointmentDetails(@PathVariable("id") Integer id, Principal principal, Model model) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			AppointmentDto appointment = appointmentService.getAppointmentDtoForOwner(id, owner.getId());
			model.addAttribute("appointment", appointment);
			return "scheduling/appointmentDetail";
		}
		catch (ResourceNotFoundException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
		}
	}

	@PostMapping("/scheduling/appointments/{id}/cancel")
	public String cancelAppointmentByOwner(@PathVariable("id") Integer id,
			@RequestParam(value = "cancellationReason", required = false) String cancellationReason,
			Principal principal, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(principal);
		try {
			appointmentService.cancelAppointmentByOwner(id, owner.getId(), cancellationReason, principal.getName());
			redirectAttributes.addFlashAttribute("successMessage", "Appointment has been cancelled successfully.");
			return "redirect:/scheduling/appointments/" + id;
		}
		catch (ResourceNotFoundException ex) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
		}
		catch (IllegalStateException | IllegalArgumentException ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/scheduling/appointments/" + id;
		}
	}

	private Owner getAuthenticatedOwner(Principal principal) {
		if (principal == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
		}
		Account account = accountRepository.findByUsernameIgnoreCase(principal.getName())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
		if (account.getOwner() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not an owner account");
		}
		return account.getOwner();
	}

}
