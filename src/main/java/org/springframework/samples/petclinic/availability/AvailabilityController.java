package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import java.util.List;
import org.springframework.samples.petclinic.availability.AvailabilityForms.ClosureForm;
import org.springframework.samples.petclinic.availability.AvailabilityForms.ExceptionDayForm;
import org.springframework.samples.petclinic.availability.AvailabilityForms.LeaveForm;
import org.springframework.samples.petclinic.availability.AvailabilityForms.ShiftForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/staff")
public class AvailabilityController {

	private final AvailabilityAdministrationService availabilityAdministrationService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final RecurringShiftRepository recurringShiftRepository;

	private final AvailabilityExceptionDayRepository availabilityExceptionDayRepository;

	private final VeterinarianLeaveRepository veterinarianLeaveRepository;

	private final ClinicClosureRepository clinicClosureRepository;

	private final VetRepository vetRepository;

	public AvailabilityController(AvailabilityAdministrationService availabilityAdministrationService,
			EffectiveAvailabilityService effectiveAvailabilityService,
			RecurringShiftRepository recurringShiftRepository,
			AvailabilityExceptionDayRepository availabilityExceptionDayRepository,
			VeterinarianLeaveRepository veterinarianLeaveRepository, ClinicClosureRepository clinicClosureRepository,
			VetRepository vetRepository) {
		this.availabilityAdministrationService = availabilityAdministrationService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.recurringShiftRepository = recurringShiftRepository;
		this.availabilityExceptionDayRepository = availabilityExceptionDayRepository;
		this.veterinarianLeaveRepository = veterinarianLeaveRepository;
		this.clinicClosureRepository = clinicClosureRepository;
		this.vetRepository = vetRepository;
	}

	@GetMapping("/clinic-policy")
	public String showClinicPolicy(Model model) {
		model.addAttribute("policy", this.effectiveAvailabilityService.getClinicPolicy());
		return "staff/clinic-policy";
	}

	@PostMapping("/clinic-policy")
	public String updateClinicPolicy(@ModelAttribute("policy") ClinicPolicy policy, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		this.availabilityAdministrationService.updateClinicPolicy(policy, actorId);
		redirectAttributes.addFlashAttribute("successMessage", "Clinic policy updated successfully.");
		return "redirect:/staff/clinic-policy";
	}

	@GetMapping("/availability/shifts")
	public String listShifts(Model model) {
		List<Vet> vets = this.vetRepository.findAll();
		model.addAttribute("vets", vets);
		model.addAttribute("shifts", this.recurringShiftRepository.findAll());
		return "staff/availability/shifts";
	}

	@GetMapping("/availability/shifts/new")
	public String newShiftForm(Model model) {
		model.addAttribute("shiftForm", new ShiftForm());
		model.addAttribute("vets", this.vetRepository.findAll());
		return "staff/availability/shift-form";
	}

	@PostMapping("/availability/shifts")
	public String createShift(@ModelAttribute("shiftForm") ShiftForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.createRecurringShift(form.getVetId(), form.getWeekday(),
					form.getLocalStart(), form.getLocalEnd(), actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Recurring shift created successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/shifts";
	}

	@GetMapping("/availability/shifts/{id}/edit")
	public String editShiftForm(@PathVariable("id") Long id, Model model) {
		RecurringShift shift = this.recurringShiftRepository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Shift not found: " + id));
		ShiftForm form = new ShiftForm();
		form.setId(shift.getId());
		form.setVetId(shift.getVetId());
		form.setWeekday(shift.getWeekday());
		form.setLocalStart(shift.getLocalStart());
		form.setLocalEnd(shift.getLocalEnd());
		model.addAttribute("shiftForm", form);
		model.addAttribute("vets", this.vetRepository.findAll());
		return "staff/availability/shift-form";
	}

	@PostMapping("/availability/shifts/{id}")
	public String updateShift(@PathVariable("id") Long id, @ModelAttribute("shiftForm") ShiftForm form,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.updateRecurringShift(id, form.getWeekday(), form.getLocalStart(),
					form.getLocalEnd(), actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Recurring shift updated successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/shifts";
	}

	@PostMapping("/availability/shifts/{id}/delete")
	public String deleteShift(@PathVariable("id") Long id, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.deleteRecurringShift(id, actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Recurring shift deleted.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/shifts";
	}

	@GetMapping("/availability/exceptions")
	public String listExceptions(Model model) {
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("exceptions", this.availabilityExceptionDayRepository.findAll());
		model.addAttribute("exceptionForm", new ExceptionDayForm());
		return "staff/availability/exception-day";
	}

	@PostMapping("/availability/exceptions")
	public String saveException(@ModelAttribute("exceptionForm") ExceptionDayForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			List<LocalTimeInterval> intervals = form.isUnavailableAllDay() ? List.of()
					: List.of(new LocalTimeInterval(form.getLocalStart(), form.getLocalEnd()));
			this.availabilityAdministrationService.setAvailabilityExceptionDay(form.getVetId(), form.getDate(),
					intervals, actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Exception day saved successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/exceptions";
	}

	@PostMapping("/availability/exceptions/{vetId}/{date}/delete")
	public String deleteException(@PathVariable("vetId") Integer vetId, @PathVariable("date") LocalDate date,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.deleteAvailabilityExceptionDay(vetId, date, actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Exception day deleted.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/exceptions";
	}

	@GetMapping("/availability/leave")
	public String listLeave(Model model) {
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("leaves", this.veterinarianLeaveRepository.findAll());
		model.addAttribute("leaveForm", new LeaveForm());
		return "staff/availability/leave-form";
	}

	@PostMapping("/availability/leave")
	public String createLeave(@ModelAttribute("leaveForm") LeaveForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.createVeterinarianLeave(form.getVetId(), form.getStartDate(),
					form.getEndDate(), form.getReasonCategory(), form.getInternalNote(), actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Veterinarian leave scheduled successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/leave";
	}

	@PostMapping("/availability/leave/{id}/delete")
	public String deleteLeave(@PathVariable("id") Long id, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.deleteVeterinarianLeave(id, actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Veterinarian leave cancelled.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/leave";
	}

	@GetMapping("/availability/closures")
	public String listClosures(Model model) {
		model.addAttribute("closures", this.clinicClosureRepository.findAll());
		model.addAttribute("closureForm", new ClosureForm());
		return "staff/availability/closure-form";
	}

	@PostMapping("/availability/closures")
	public String createClosure(@ModelAttribute("closureForm") ClosureForm form, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.createClinicClosure(form.getStartDate(), form.getEndDate(),
					form.getOwnerReason(), form.getInternalNote(), actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Clinic closure scheduled successfully.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/closures";
	}

	@PostMapping("/availability/closures/{id}/delete")
	public String deleteClosure(@PathVariable("id") Long id, Authentication authentication,
			RedirectAttributes redirectAttributes) {
		Long actorId = extractActorId(authentication);
		try {
			this.availabilityAdministrationService.deleteClinicClosure(id, actorId);
			redirectAttributes.addFlashAttribute("successMessage", "Clinic closure cancelled.");
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
		}
		return "redirect:/staff/availability/closures";
	}

	private Long extractActorId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			return principal.getAccountId();
		}
		return 1L;
	}

}
