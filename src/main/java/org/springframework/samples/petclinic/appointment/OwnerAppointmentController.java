package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Duration;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/owner/appointments/{appointmentId}")
public class OwnerAppointmentController {

	private final AppointmentRepository appointmentRepository;

	private final OwnerAppointmentService appointmentService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final Clock clock;

	public OwnerAppointmentController(AppointmentRepository appointmentRepository,
			OwnerAppointmentService appointmentService, OwnerRepository ownerRepository, VetRepository vetRepository,
			AppointmentLifecyclePolicy lifecyclePolicy, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.appointmentService = appointmentService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.lifecyclePolicy = lifecyclePolicy;
		this.clock = clock;
	}

	@GetMapping
	public String appointmentDetail(@PathVariable("appointmentId") Long appointmentId, Authentication authentication,
			Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Appointment appointment = this.appointmentRepository.findByIdAndOwnerId(appointmentId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		Owner owner = this.ownerRepository.findById(ownerId).orElse(null);
		Pet pet = (owner != null) ? owner.getPet(appointment.getPetId()) : null;
		Vet vet = this.vetRepository.findById(appointment.getVetId()).orElse(null);

		model.addAttribute("appointment", appointment);
		model.addAttribute("pet", pet);
		model.addAttribute("vet", vet);
		model.addAttribute("canCancel", this.lifecyclePolicy.canOwnerCancel(appointment, this.clock.instant()));
		model.addAttribute("durationMinutes",
				Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes());
		return "owner/appointments/detail";
	}

	@GetMapping("/cancel")
	public String cancelAppointmentForm(@PathVariable("appointmentId") Long appointmentId,
			Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Appointment appointment = this.appointmentRepository.findByIdAndOwnerId(appointmentId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		model.addAttribute("appointment", appointment);
		return "owner/appointments/cancel";
	}

	@PostMapping("/cancel")
	public String processCancelAppointment(@PathVariable("appointmentId") Long appointmentId,
			@RequestParam(name = "reason", required = false) String reason, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		this.appointmentService.cancelAppointmentByOwner(appointmentId, ownerId, reason);
		return "redirect:/owner/dashboard";
	}

	private Integer extractOwnerId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.getOwnerId() != null) {
				return principal.getOwnerId();
			}
		}
		throw new AccessDeniedException("Authenticated owner principal required");
	}

}
