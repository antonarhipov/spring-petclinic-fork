package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.samples.petclinic.appointment.DirectBookingService.DirectBookingRequest;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff/appointments")
public class StaffDirectBookingController {

	private final DirectBookingService directBookingService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository appointmentChangeEventRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final Clock clock;

	public StaffDirectBookingController(DirectBookingService directBookingService,
			EffectiveAvailabilityService effectiveAvailabilityService, AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository appointmentChangeEventRepository, OwnerRepository ownerRepository,
			VetRepository vetRepository, AppointmentLifecyclePolicy lifecyclePolicy, Clock clock) {
		this.directBookingService = directBookingService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.appointmentRepository = appointmentRepository;
		this.appointmentChangeEventRepository = appointmentChangeEventRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.lifecyclePolicy = lifecyclePolicy;
		this.clock = clock;
	}

	@GetMapping("/direct-book")
	public String directBookForm(@RequestParam(name = "ownerId", required = false) Integer ownerId,
			@RequestParam(name = "vetId", required = false) Integer vetId, Model model) {
		DirectBookingForm form = new DirectBookingForm();
		if (ownerId != null) {
			form.setOwnerId(ownerId);
		}
		if (vetId != null) {
			form.setVetId(vetId);
		}
		populateReferenceData(model, form.getOwnerId());
		model.addAttribute("directBookingForm", form);
		return "staff/appointments/direct-book";
	}

	@PostMapping("/direct-book")
	public String reviewDirectBooking(@ModelAttribute("directBookingForm") DirectBookingForm form, Model model) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		if (form.getDate() == null || form.getStartTime() == null || form.getDurationMinutes() == null
				|| form.getInternalReason() == null || form.getInternalReason().isBlank()) {
			populateReferenceData(model, form.getOwnerId());
			model.addAttribute("errorMessage", "Date, start time, duration, and internal reason are required.");
			return "staff/appointments/direct-book";
		}

		Instant startAt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId).toInstant();
		Instant endAt = startAt.plus(Duration.ofMinutes(form.getDurationMinutes()));

		DirectBookingRequest request = new DirectBookingRequest(form.getOwnerId(), form.getPetId(), form.getVetId(),
				startAt, endAt, form.isOwnerAgreementRecorded(), form.getAgreementMedium(), form.getReasonCategory(),
				form.getInternalReason(), null, null);

		BookingConflictCheck check = this.directBookingService.validateDirectBooking(request);
		if (!check.valid()) {
			populateReferenceData(model, form.getOwnerId());
			model.addAttribute("errorMessage", String.join(", ", check.errorMessages()));
			return "staff/appointments/direct-book";
		}

		Owner owner = this.ownerRepository.findById(form.getOwnerId()).orElse(null);
		Pet pet = owner != null ? owner.getPet(form.getPetId()) : null;
		Vet vet = this.vetRepository.findById(form.getVetId()).orElse(null);

		model.addAttribute("form", form);
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("vet", vet);
		model.addAttribute("startAt", startAt);
		model.addAttribute("endAt", endAt);
		return "staff/appointments/direct-book-review";
	}

	@PostMapping("/direct-book-review")
	public String confirmDirectBooking(@ModelAttribute("form") DirectBookingForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant startAt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId).toInstant();
		Instant endAt = startAt.plus(Duration.ofMinutes(form.getDurationMinutes()));
		Long actorAccountId = extractActorId(authentication);

		DirectBookingRequest request = new DirectBookingRequest(form.getOwnerId(), form.getPetId(), form.getVetId(),
				startAt, endAt, form.isOwnerAgreementRecorded(), form.getAgreementMedium(), form.getReasonCategory(),
				form.getInternalReason(), actorAccountId, null);

		try {
			Appointment appointment = this.directBookingService.bookDirectly(request);
			redirectAttributes.addFlashAttribute("successMessage", "Appointment booked successfully.");
			return "redirect:/staff/appointments/" + appointment.getId();
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
			return "redirect:/staff/appointments/direct-book";
		}
	}

	@GetMapping("/{id}")
	public String viewAppointment(@PathVariable("id") Long id, Model model) {
		Appointment appointment = this.appointmentRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));

		Owner owner = this.ownerRepository.findById(appointment.getOwnerId()).orElse(null);
		Pet pet = owner != null ? owner.getPet(appointment.getPetId()) : null;
		Vet vet = this.vetRepository.findById(appointment.getVetId()).orElse(null);
		List<AppointmentChangeEvent> changeEvents = this.appointmentChangeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(id);

		model.addAttribute("appointment", appointment);
		model.addAttribute("owner", owner);
		model.addAttribute("pet", pet);
		model.addAttribute("vet", vet);
		model.addAttribute("changeEvents", changeEvents);
		model.addAttribute("zoneId", this.effectiveAvailabilityService.getClinicZoneId());
		Instant now = this.clock.instant();
		model.addAttribute("canCancel", this.lifecyclePolicy.canStaffCancel(appointment, now));
		model.addAttribute("canReschedule", this.lifecyclePolicy.canReschedule(appointment, now));
		model.addAttribute("canComplete", this.lifecyclePolicy.canComplete(appointment, now));
		model.addAttribute("canRecordNoShow", this.lifecyclePolicy.canRecordNoShow(appointment, now));
		model.addAttribute("canCorrectOutcome", this.lifecyclePolicy.canCorrectOutcome(appointment, now));
		return "staff/appointments/detail";
	}

	private void populateReferenceData(Model model, Integer selectedOwnerId) {
		List<Owner> allOwners = this.ownerRepository.findAll();
		model.addAttribute("allOwners", allOwners);
		model.addAttribute("allVets", this.vetRepository.findAll());
		model.addAttribute("allowedDurations",
				this.effectiveAvailabilityService.getClinicPolicy().getAllowedDurations());

		if (selectedOwnerId != null) {
			Owner selectedOwner = this.ownerRepository.findById(selectedOwnerId).orElse(null);
			if (selectedOwner != null) {
				model.addAttribute("pets", selectedOwner.getPets());
			}
		}
	}

	private Long extractActorId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			return principal.getAccountId();
		}
		return 1L;
	}

}
