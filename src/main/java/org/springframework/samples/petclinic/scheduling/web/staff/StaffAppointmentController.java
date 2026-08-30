package org.springframework.samples.petclinic.scheduling.web.staff;

import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.account.AccountRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentCorrectionService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentReasonCategory;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.StaffCalendarQueryService;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.system.StaleStateException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class StaffAppointmentController {

	private final StaffCalendarQueryService calendar;

	private final AppointmentLifecycleService lifecycle;

	private final AppointmentCorrectionService corrections;

	private final AccountRepository accounts;

	public StaffAppointmentController(StaffCalendarQueryService calendar, AppointmentLifecycleService lifecycle,
			AppointmentCorrectionService corrections, AccountRepository accounts) {
		this.calendar = calendar;
		this.lifecycle = lifecycle;
		this.corrections = corrections;
		this.accounts = accounts;
	}

	@GetMapping("/staff/appointments/{id}")
	public String detail(@PathVariable Long id, Model model) {
		model.addAttribute("appointment", this.calendar.require(id));
		return "scheduling/staff/appointment-detail";
	}

	@GetMapping("/staff/appointments/{id}/reschedule")
	public String rescheduleForm(@PathVariable Long id, Model model) {
		model.addAttribute("appointment", this.calendar.require(id));
		model.addAttribute("form", new StaffAppointmentForm());
		return "scheduling/staff/appointment-form";
	}

	@PostMapping("/staff/appointments/{id}/reschedule")
	public String reschedule(@PathVariable Long id, @ModelAttribute StaffAppointmentForm form,
			Authentication authentication) {
		Appointment appointment = this.calendar.require(id);
		assertVersion(form.getExpectedVersion(), appointment);
		this.lifecycle.reschedule(id,
				new CandidateSlot(form.getVeterinarianId() + "@" + form.getStartAt(), form.getVeterinarianId(),
						form.getStartAt(), form.getEndAt(), "STAFF", 0),
				staff(authentication).getId(),
				form.getReasonCategory() == null ? AppointmentReasonCategory.CLINIC_INITIATED
						: AppointmentReasonCategory.valueOf(form.getReasonCategory()),
				form.getNote());
		return "redirect:/staff/appointments/" + id;
	}

	@GetMapping("/staff/appointments/{id}/outcome")
	public String outcomeForm(@PathVariable Long id, Model model) {
		model.addAttribute("appointment", this.calendar.require(id));
		model.addAttribute("form", new StaffAppointmentForm());
		return "scheduling/staff/appointment-outcome-form";
	}

	@PostMapping("/staff/appointments/{id}/complete")
	public String complete(@PathVariable Long id, @ModelAttribute StaffAppointmentForm form,
			Authentication authentication) {
		this.lifecycle.complete(id, staff(authentication).getId(), form.getNote());
		return "redirect:/staff/appointments/" + id;
	}

	@PostMapping("/staff/appointments/{id}/no-show")
	public String noShow(@PathVariable Long id, @ModelAttribute StaffAppointmentForm form,
			Authentication authentication) {
		this.lifecycle.markNoShow(id, staff(authentication).getId(), form.getNote());
		return "redirect:/staff/appointments/" + id;
	}

	@PostMapping("/staff/appointments/{id}/cancel")
	public String cancel(@PathVariable Long id, @ModelAttribute StaffAppointmentForm form,
			Authentication authentication) {
		this.lifecycle.cancel(id, staff(authentication).getId(), "STAFF",
				form.getReasonCategory() == null ? AppointmentReasonCategory.CLINIC_INITIATED
						: AppointmentReasonCategory.valueOf(form.getReasonCategory()),
				form.getNote());
		return "redirect:/staff/calendar";
	}

	@GetMapping("/staff/appointments/{id}/correction")
	public String correctionForm(@PathVariable Long id, Model model) {
		model.addAttribute("appointment", this.calendar.require(id));
		model.addAttribute("form", new AppointmentCorrectionForm());
		return "scheduling/staff/appointment-correction";
	}

	@PostMapping("/staff/appointments/{id}/correction")
	public String correct(@PathVariable Long id, @ModelAttribute AppointmentCorrectionForm form,
			Authentication authentication) {
		this.corrections.correct(id, AppointmentStatus.valueOf(form.getTargetStatus()), staff(authentication).getId(),
				form.getNote());
		return "redirect:/staff/appointments/" + id;
	}

	private void assertVersion(Integer expectedVersion, Appointment appointment) {
		if (expectedVersion != null && !expectedVersion.equals(appointment.getVersion())) {
			throw new StaleStateException("stale", appointment, expectedVersion);
		}
	}

	private Account staff(Authentication authentication) {
		return this.accounts.findByUsername(authentication.getName()).orElseThrow();
	}

}
