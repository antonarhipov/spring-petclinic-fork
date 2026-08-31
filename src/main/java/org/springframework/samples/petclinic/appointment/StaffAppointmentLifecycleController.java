package org.springframework.samples.petclinic.appointment;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.availability.CapacityConflictService.BookingConflictCheck;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/staff/appointments/{appointmentId}")
public class StaffAppointmentLifecycleController {

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeEventRepository appointmentChangeEventRepository;

	private final AppointmentOutcomeService outcomeService;

	private final AppointmentCorrectionService correctionService;

	private final StaffAppointmentReschedulingService reschedulingService;

	private final StaffAppointmentCancellationService cancellationService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final AppointmentLifecyclePolicy lifecyclePolicy;

	private final Clock clock;

	public StaffAppointmentLifecycleController(AppointmentRepository appointmentRepository,
			AppointmentChangeEventRepository appointmentChangeEventRepository, AppointmentOutcomeService outcomeService,
			AppointmentCorrectionService correctionService, StaffAppointmentReschedulingService reschedulingService,
			StaffAppointmentCancellationService cancellationService, OwnerRepository ownerRepository,
			VetRepository vetRepository, EffectiveAvailabilityService effectiveAvailabilityService,
			AppointmentLifecyclePolicy lifecyclePolicy, Clock clock) {
		this.appointmentRepository = appointmentRepository;
		this.appointmentChangeEventRepository = appointmentChangeEventRepository;
		this.outcomeService = outcomeService;
		this.correctionService = correctionService;
		this.reschedulingService = reschedulingService;
		this.cancellationService = cancellationService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.lifecyclePolicy = lifecyclePolicy;
		this.clock = clock;
	}

	public static class CompleteAppointmentForm {

		private String clinicalNotes;

		private String ownerVisibleSummary;

		public String getClinicalNotes() {
			return this.clinicalNotes;
		}

		public void setClinicalNotes(String clinicalNotes) {
			this.clinicalNotes = clinicalNotes;
		}

		public String getOwnerVisibleSummary() {
			return this.ownerVisibleSummary;
		}

		public void setOwnerVisibleSummary(String ownerVisibleSummary) {
			this.ownerVisibleSummary = ownerVisibleSummary;
		}

	}

	public static class NoShowForm {

		private String reason;

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

	}

	public static class CorrectOutcomeForm {

		private OutcomeState newOutcome;

		private String reason;

		private String clinicalNotes;

		private String ownerVisibleSummary;

		public OutcomeState getNewOutcome() {
			return this.newOutcome;
		}

		public void setNewOutcome(OutcomeState newOutcome) {
			this.newOutcome = newOutcome;
		}

		public String getReason() {
			return this.reason;
		}

		public void setReason(String reason) {
			this.reason = reason;
		}

		public String getClinicalNotes() {
			return this.clinicalNotes;
		}

		public void setClinicalNotes(String clinicalNotes) {
			this.clinicalNotes = clinicalNotes;
		}

		public String getOwnerVisibleSummary() {
			return this.ownerVisibleSummary;
		}

		public void setOwnerVisibleSummary(String ownerVisibleSummary) {
			this.ownerVisibleSummary = ownerVisibleSummary;
		}

	}

	public static class RescheduleAppointmentForm {

		private Integer vetId;

		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate date;

		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
		private LocalTime startTime;

		private Integer durationMinutes = 30;

		private boolean ownerAgreementRecorded = false;

		private String agreementMedium = "PHONE";

		private String internalReason;

		private String reasonCategory = "OWNER_REQUEST";

		private String ownerExplanation;

		public Integer getVetId() {
			return this.vetId;
		}

		public void setVetId(Integer vetId) {
			this.vetId = vetId;
		}

		public LocalDate getDate() {
			return this.date;
		}

		public void setDate(LocalDate date) {
			this.date = date;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public Integer getDurationMinutes() {
			return this.durationMinutes;
		}

		public void setDurationMinutes(Integer durationMinutes) {
			this.durationMinutes = durationMinutes;
		}

		public boolean isOwnerAgreementRecorded() {
			return this.ownerAgreementRecorded;
		}

		public void setOwnerAgreementRecorded(boolean ownerAgreementRecorded) {
			this.ownerAgreementRecorded = ownerAgreementRecorded;
		}

		public String getAgreementMedium() {
			return this.agreementMedium;
		}

		public void setAgreementMedium(String agreementMedium) {
			this.agreementMedium = agreementMedium;
		}

		public String getInternalReason() {
			return this.internalReason;
		}

		public String getReasonCategory() {
			return this.reasonCategory;
		}

		public void setReasonCategory(String reasonCategory) {
			this.reasonCategory = reasonCategory;
		}

		public void setInternalReason(String internalReason) {
			this.internalReason = internalReason;
		}

		public String getOwnerExplanation() {
			return this.ownerExplanation;
		}

		public void setOwnerExplanation(String ownerExplanation) {
			this.ownerExplanation = ownerExplanation;
		}

	}

	public static class StaffCancellationForm {

		private String reasonCategory;

		private String reasonDetails;

		private String ownerExplanation;

		private boolean ownerContacted = false;

		public String getReasonCategory() {
			return this.reasonCategory;
		}

		public void setReasonCategory(String reasonCategory) {
			this.reasonCategory = reasonCategory;
		}

		public String getReasonDetails() {
			return this.reasonDetails;
		}

		public void setReasonDetails(String reasonDetails) {
			this.reasonDetails = reasonDetails;
		}

		public String getOwnerExplanation() {
			return this.ownerExplanation;
		}

		public void setOwnerExplanation(String ownerExplanation) {
			this.ownerExplanation = ownerExplanation;
		}

		public boolean isOwnerContacted() {
			return this.ownerContacted;
		}

		public void setOwnerContacted(boolean ownerContacted) {
			this.ownerContacted = ownerContacted;
		}

	}

	@GetMapping("/complete")
	public String completeForm(@PathVariable("appointmentId") Long appointmentId, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		model.addAttribute("appointment", appointment);
		model.addAttribute("form", new CompleteAppointmentForm());
		return "staff/appointments/complete";
	}

	@PostMapping("/complete")
	public String processComplete(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") CompleteAppointmentForm form, Authentication authentication) {
		Long actorAccountId = extractAccountId(authentication);
		AppointmentOutcomeService.CompleteAppointmentCommand cmd = new AppointmentOutcomeService.CompleteAppointmentCommand(
				appointmentId, actorAccountId, form.getClinicalNotes(), form.getOwnerVisibleSummary());
		this.outcomeService.completeAppointment(cmd);
		return "redirect:/staff/calendar";
	}

	@GetMapping("/no-show")
	public String noShowForm(@PathVariable("appointmentId") Long appointmentId, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		model.addAttribute("appointment", appointment);
		model.addAttribute("form", new NoShowForm());
		return "staff/appointments/no-show";
	}

	@PostMapping("/no-show")
	public String processNoShow(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") NoShowForm form, Authentication authentication) {
		Long actorAccountId = extractAccountId(authentication);
		AppointmentOutcomeService.NoShowCommand cmd = new AppointmentOutcomeService.NoShowCommand(appointmentId,
				actorAccountId, form.getReason());
		this.outcomeService.recordNoShow(cmd);
		return "redirect:/staff/calendar";
	}

	@GetMapping("/correct-outcome")
	public String correctOutcomeForm(@PathVariable("appointmentId") Long appointmentId, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		model.addAttribute("appointment", appointment);
		model.addAttribute("form", new CorrectOutcomeForm());
		return "staff/appointments/correct-outcome";
	}

	@PostMapping("/correct-outcome")
	public String processCorrectOutcome(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") CorrectOutcomeForm form, Authentication authentication) {
		Long actorAccountId = extractAccountId(authentication);
		AppointmentCorrectionService.CorrectOutcomeCommand cmd = new AppointmentCorrectionService.CorrectOutcomeCommand(
				appointmentId, actorAccountId, form.getNewOutcome(), form.getReason(), form.getClinicalNotes(),
				form.getOwnerVisibleSummary());
		this.correctionService.correctOutcome(cmd);
		return "redirect:/staff/calendar";
	}

	@GetMapping("/reschedule")
	public String rescheduleForm(@PathVariable("appointmentId") Long appointmentId, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		ZonedDateTime start = ZonedDateTime.ofInstant(appointment.getStartAt(), zoneId);
		RescheduleAppointmentForm form = new RescheduleAppointmentForm();
		form.setVetId(appointment.getVetId());
		form.setDate(start.toLocalDate());
		form.setStartTime(start.toLocalTime());
		form.setDurationMinutes(
				(int) java.time.Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes());
		populateRescheduleModel(model, appointment, form);
		return "staff/appointments/reschedule";
	}

	@PostMapping("/reschedule")
	public String processReschedule(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") RescheduleAppointmentForm form, Authentication authentication, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
		if (form.getDate() == null || form.getStartTime() == null || form.getDurationMinutes() == null
				|| form.getVetId() == null) {
			populateRescheduleModel(model, appointment, form);
			model.addAttribute("errorMessage", "Veterinarian, date, time, and duration are required.");
			return "staff/appointments/reschedule";
		}
		Long actorAccountId = extractAccountId(authentication);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		ZonedDateTime startZdt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId);
		Instant startAt = startZdt.toInstant();
		Instant endAt = startAt.plusSeconds(form.getDurationMinutes() * 60L);

		StaffAppointmentReschedulingService.RescheduleAppointmentCommand cmd = new StaffAppointmentReschedulingService.RescheduleAppointmentCommand(
				appointmentId, actorAccountId, form.getVetId(), startAt, endAt, form.isOwnerAgreementRecorded(),
				form.getAgreementMedium(), form.getReasonCategory(), form.getInternalReason(),
				form.getOwnerExplanation());
		try {
			BookingConflictCheck check = this.reschedulingService.validateReschedule(cmd);
			if (!check.valid()) {
				populateRescheduleModel(model, appointment, form);
				model.addAttribute("errorMessage", String.join(", ", check.errorMessages()));
				return "staff/appointments/reschedule";
			}
		}
		catch (IllegalArgumentException ex) {
			populateRescheduleModel(model, appointment, form);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/appointments/reschedule";
		}
		model.addAttribute("appointment", appointment);
		model.addAttribute("vet", this.vetRepository.findById(form.getVetId()).orElse(null));
		model.addAttribute("form", form);
		model.addAttribute("startAt", startAt);
		model.addAttribute("endAt", endAt);
		return "staff/appointments/reschedule-review";
	}

	@PostMapping("/reschedule-review")
	public String confirmReschedule(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") RescheduleAppointmentForm form, Authentication authentication) {
		Long actorAccountId = extractAccountId(authentication);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		Instant startAt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId).toInstant();
		Instant endAt = startAt.plusSeconds(form.getDurationMinutes() * 60L);
		StaffAppointmentReschedulingService.RescheduleAppointmentCommand cmd = new StaffAppointmentReschedulingService.RescheduleAppointmentCommand(
				appointmentId, actorAccountId, form.getVetId(), startAt, endAt, form.isOwnerAgreementRecorded(),
				form.getAgreementMedium(), form.getReasonCategory(), form.getInternalReason(),
				form.getOwnerExplanation());
		this.reschedulingService.rescheduleAppointment(cmd);
		return "redirect:/staff/calendar";
	}

	@GetMapping("/cancel")
	public String cancelForm(@PathVariable("appointmentId") Long appointmentId, Model model) {
		Appointment appointment = this.appointmentRepository.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

		populateCancellationModel(model, appointment, new StaffCancellationForm());
		return "staff/appointments/cancel";
	}

	@PostMapping("/cancel")
	public String processCancel(@PathVariable("appointmentId") Long appointmentId,
			@ModelAttribute("form") StaffCancellationForm form, Authentication authentication, Model model) {
		Long actorAccountId = extractAccountId(authentication);
		StaffAppointmentCancellationService.StaffCancellationCommand cmd = new StaffAppointmentCancellationService.StaffCancellationCommand(
				appointmentId, actorAccountId, form.getReasonCategory(), form.getReasonDetails(),
				form.getOwnerExplanation(), form.isOwnerContacted());
		try {
			this.cancellationService.cancelAppointmentByStaff(cmd);
			return "redirect:/staff/calendar";
		}
		catch (IllegalArgumentException | IllegalStateException ex) {
			Appointment appointment = this.appointmentRepository.findById(appointmentId)
				.orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
			populateCancellationModel(model, appointment, form);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/appointments/cancel";
		}
	}

	private Long extractAccountId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.getAccountId() != null) {
				return principal.getAccountId();
			}
		}
		throw new AccessDeniedException("Authenticated staff principal required");
	}

	private void populateRescheduleModel(Model model, Appointment appointment, RescheduleAppointmentForm form) {
		model.addAttribute("appointment", appointment);
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("allowedDurations",
				this.effectiveAvailabilityService.getClinicPolicy().getAllowedDurations());
		model.addAttribute("form", form);
	}

	private void populateCancellationModel(Model model, Appointment appointment, StaffCancellationForm form) {
		boolean priorOwnerAgreement = this.appointmentChangeEventRepository
			.findByAppointmentIdOrderByOccurredAtAsc(appointment.getId())
			.stream()
			.anyMatch(AppointmentChangeEvent::isOwnerAgreementRecorded);
		model.addAttribute("appointment", appointment);
		model.addAttribute("priorOwnerAgreement", priorOwnerAgreement);
		model.addAttribute("form", form);
	}

}
