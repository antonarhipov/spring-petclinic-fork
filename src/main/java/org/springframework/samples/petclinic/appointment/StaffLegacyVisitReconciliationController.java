package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.owner.VisitRepository;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
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

@Controller
@RequestMapping("/staff/legacy-visits")
public class StaffLegacyVisitReconciliationController {

	private final LegacyVisitReconciliationService reconciliationService;

	private final VisitRepository visitRepository;

	private final VetRepository vetRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	public StaffLegacyVisitReconciliationController(LegacyVisitReconciliationService reconciliationService,
			VisitRepository visitRepository, VetRepository vetRepository,
			EffectiveAvailabilityService effectiveAvailabilityService) {
		this.reconciliationService = reconciliationService;
		this.visitRepository = visitRepository;
		this.vetRepository = vetRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
	}

	public static class ReconcileVisitForm {

		private Integer vetId;

		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate date;

		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
		private LocalTime startTime = LocalTime.of(9, 0);

		private Integer durationMinutes = 30;

		private boolean ownerAgreementRecorded = false;

		private String agreementMedium = "PHONE";

		private String internalReason;

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

		public void setInternalReason(String internalReason) {
			this.internalReason = internalReason;
		}

	}

	@GetMapping
	public String listLegacyVisits(Model model) {
		List<LegacyVisitReconciliationService.LegacyVisitSummaryDto> visits = this.reconciliationService
			.findUnreconciledFutureLegacyVisits();
		model.addAttribute("visits", visits);
		return "staff/legacy-visits/list";
	}

	@GetMapping("/{visitId}/reconcile")
	public String reconcileForm(@PathVariable("visitId") Integer visitId, Model model) {
		Visit visit = this.visitRepository.findById(visitId)
			.orElseThrow(() -> new IllegalArgumentException("Visit not found: " + visitId));

		ReconcileVisitForm form = new ReconcileVisitForm();
		form.setDate(visit.getDate());

		model.addAttribute("visit", visit);
		model.addAttribute("vets", this.vetRepository.findAll());
		model.addAttribute("form", form);
		return "staff/legacy-visits/reconcile";
	}

	@PostMapping("/{visitId}/reconcile")
	public String processReconcile(@PathVariable("visitId") Integer visitId,
			@ModelAttribute("form") ReconcileVisitForm form, Authentication authentication) {
		Long actorAccountId = extractAccountId(authentication);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();
		ZonedDateTime startZdt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId);
		Instant startAt = startZdt.toInstant();
		Instant endAt = startAt.plusSeconds(form.getDurationMinutes() * 60L);

		LegacyVisitReconciliationService.ReconcileLegacyVisitCommand cmd = new LegacyVisitReconciliationService.ReconcileLegacyVisitCommand(
				visitId, actorAccountId, form.getVetId(), startAt, endAt, form.isOwnerAgreementRecorded(),
				form.getAgreementMedium(), form.getInternalReason());

		this.reconciliationService.reconcileLegacyVisit(cmd);
		return "redirect:/staff/legacy-visits";
	}

	private Long extractAccountId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.getAccountId() != null) {
				return principal.getAccountId();
			}
		}
		throw new AccessDeniedException("Authenticated staff principal required");
	}

}
