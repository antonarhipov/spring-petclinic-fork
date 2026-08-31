package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.samples.petclinic.scheduling.interpretation.Urgency;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferLifecycleService;
import org.springframework.samples.petclinic.scheduling.offer.OfferRepository;
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
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/owner/requests/{requestId}")
public class OwnerRequestManagementController {

	private final OwnerRequestRevisionService revisionService;

	private final OwnerInterpretationService interpretationService;

	private final OfferLifecycleService offerLifecycleService;

	private final RequestWithdrawalService withdrawalService;

	private final OfferRepository offerRepository;

	private final SchedulingRequestRepository requestRepository;

	private final VetRepository vetRepository;

	public OwnerRequestManagementController(OwnerRequestRevisionService revisionService,
			OwnerInterpretationService interpretationService, OfferLifecycleService offerLifecycleService,
			RequestWithdrawalService withdrawalService, OfferRepository offerRepository,
			SchedulingRequestRepository requestRepository, VetRepository vetRepository) {
		this.revisionService = revisionService;
		this.interpretationService = interpretationService;
		this.offerLifecycleService = offerLifecycleService;
		this.withdrawalService = withdrawalService;
		this.offerRepository = offerRepository;
		this.requestRepository = requestRepository;
		this.vetRepository = vetRepository;
	}

	public static class StructuredRevisionForm {

		private String visitReason;

		private Integer durationMinutes = 30;

		private Integer preferredVetId;

		private Integer requiredSpecialtyId;

		private Urgency urgency = Urgency.ROUTINE;

		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate date;

		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate rangeStart;

		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
		private LocalDate rangeEnd;

		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
		private LocalTime startTime = LocalTime.of(9, 0);

		@DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
		private LocalTime endTime = LocalTime.of(17, 0);

		private String weekdays = "MON,TUE,WED,THU,FRI";

		public String getVisitReason() {
			return this.visitReason;
		}

		public void setVisitReason(String visitReason) {
			this.visitReason = visitReason;
		}

		public Integer getDurationMinutes() {
			return this.durationMinutes;
		}

		public void setDurationMinutes(Integer durationMinutes) {
			this.durationMinutes = durationMinutes;
		}

		public Integer getPreferredVetId() {
			return this.preferredVetId;
		}

		public void setPreferredVetId(Integer preferredVetId) {
			this.preferredVetId = preferredVetId;
		}

		public Integer getRequiredSpecialtyId() {
			return this.requiredSpecialtyId;
		}

		public void setRequiredSpecialtyId(Integer requiredSpecialtyId) {
			this.requiredSpecialtyId = requiredSpecialtyId;
		}

		public Urgency getUrgency() {
			return this.urgency;
		}

		public void setUrgency(Urgency urgency) {
			this.urgency = urgency;
		}

		public LocalDate getDate() {
			return this.date;
		}

		public void setDate(LocalDate date) {
			this.date = date;
		}

		public LocalDate getRangeStart() {
			return this.rangeStart;
		}

		public void setRangeStart(LocalDate rangeStart) {
			this.rangeStart = rangeStart;
		}

		public LocalDate getRangeEnd() {
			return this.rangeEnd;
		}

		public void setRangeEnd(LocalDate rangeEnd) {
			this.rangeEnd = rangeEnd;
		}

		public LocalTime getStartTime() {
			return this.startTime;
		}

		public void setStartTime(LocalTime startTime) {
			this.startTime = startTime;
		}

		public LocalTime getEndTime() {
			return this.endTime;
		}

		public void setEndTime(LocalTime endTime) {
			this.endTime = endTime;
		}

		public String getWeekdays() {
			return this.weekdays;
		}

		public void setWeekdays(String weekdays) {
			this.weekdays = weekdays;
		}

	}

	public static class ProseRevisionForm {

		private String prose;

		private boolean aiConsent = true;

		public String getProse() {
			return this.prose;
		}

		public void setProse(String prose) {
			this.prose = prose;
		}

		public boolean isAiConsent() {
			return this.aiConsent;
		}

		public void setAiConsent(boolean aiConsent) {
			this.aiConsent = aiConsent;
		}

	}

	@GetMapping("/interpretation/edit")
	public String editInterpretationForm(@PathVariable("requestId") Long requestId, Authentication authentication,
			Model model) {
		Integer ownerId = extractOwnerId(authentication);
		OwnerInterpretationService.InterpretationReviewDto review = this.interpretationService
			.getInterpretationForReview(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Interpretation details not found"));

		StructuredRevisionForm form = new StructuredRevisionForm();
		form.setVisitReason(review.visitReason());
		form.setDurationMinutes(review.durationMinutes() != null ? review.durationMinutes() : 30);
		form.setPreferredVetId(review.preferredVetId());
		form.setRequiredSpecialtyId(review.requiredSpecialtyId());
		form.setUrgency(review.urgency() != null ? review.urgency() : Urgency.ROUTINE);

		model.addAttribute("requestId", requestId);
		model.addAttribute("form", form);
		model.addAttribute("vets", this.vetRepository.findAll());
		return "owner/requests/interpretation-edit";
	}

	@PostMapping("/interpretation/edit")
	public String processEditInterpretation(@PathVariable("requestId") Long requestId,
			@ModelAttribute("form") StructuredRevisionForm form, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);

		List<AvailabilityWindow> windows = new ArrayList<>();
		if (form.getDate() != null) {
			AvailabilityWindow w = new AvailabilityWindow(null, WindowClassification.ALLOWED, WindowShape.ONE_OFF,
					form.getDate(), null, null, null,
					form.getStartTime() != null ? form.getStartTime() : LocalTime.of(9, 0),
					form.getEndTime() != null ? form.getEndTime() : LocalTime.of(17, 0), "Owner structured edit", null);
			windows.add(w);
		}
		else if (form.getRangeStart() != null && form.getRangeEnd() != null) {
			AvailabilityWindow w = new AvailabilityWindow(null, WindowClassification.ALLOWED, WindowShape.WEEKLY, null,
					form.getRangeStart(), form.getRangeEnd(),
					form.getWeekdays() != null ? form.getWeekdays() : "MON,TUE,WED,THU,FRI",
					form.getStartTime() != null ? form.getStartTime() : LocalTime.of(9, 0),
					form.getEndTime() != null ? form.getEndTime() : LocalTime.of(17, 0), "Owner structured edit", null);
			windows.add(w);
		}

		OwnerRequestRevisionService.StructuredRevisionCommand cmd = new OwnerRequestRevisionService.StructuredRevisionCommand(
				requestId, ownerId, form.getVisitReason(), form.getDurationMinutes(), form.getPreferredVetId(),
				form.getRequiredSpecialtyId(), form.getUrgency(), windows);

		this.revisionService.reviseStructured(cmd);
		return "redirect:/owner/requests/" + requestId;
	}

	@GetMapping("/text-revision")
	public String textRevisionForm(@PathVariable("requestId") Long requestId, Authentication authentication,
			Model model) {
		Integer ownerId = extractOwnerId(authentication);
		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		model.addAttribute("requestId", requestId);
		model.addAttribute("form", new ProseRevisionForm());
		return "owner/requests/text-revision";
	}

	@PostMapping("/text-revision")
	public String processTextRevision(@PathVariable("requestId") Long requestId,
			@ModelAttribute("form") ProseRevisionForm form, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);

		OwnerRequestRevisionService.ProseRevisionCommand cmd = new OwnerRequestRevisionService.ProseRevisionCommand(
				requestId, ownerId, form.getProse(), form.isAiConsent());

		this.revisionService.reviseProse(cmd);
		return "redirect:/owner/requests/" + requestId;
	}

	@GetMapping("/offers/{offerId}/reject")
	public String rejectOfferForm(@PathVariable("requestId") Long requestId, @PathVariable("offerId") Long offerId,
			Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		Offer offer = this.offerRepository.findByIdAndOwnerId(offerId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Offer not found"));

		model.addAttribute("requestId", requestId);
		model.addAttribute("offer", offer);
		return "owner/requests/reject-offer";
	}

	@PostMapping("/offers/{offerId}/reject")
	public String processRejectOffer(@PathVariable("requestId") Long requestId, @PathVariable("offerId") Long offerId,
			@RequestParam(name = "reason", required = false) String reason, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		this.offerLifecycleService.rejectOffer(offerId, ownerId, reason);
		return "redirect:/owner/requests/" + requestId;
	}

	@PostMapping("/match")
	public String requestNextOffer(@PathVariable("requestId") Long requestId, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		this.offerLifecycleService.requestNextOffer(requestId, ownerId);
		return "redirect:/owner/requests/" + requestId;
	}

	@GetMapping("/withdraw")
	public String withdrawForm(@PathVariable("requestId") Long requestId, Authentication authentication, Model model) {
		Integer ownerId = extractOwnerId(authentication);
		SchedulingRequest request = this.requestRepository.findByIdAndOwnerId(requestId, ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Request not found"));

		model.addAttribute("requestId", requestId);
		model.addAttribute("request", request);
		return "owner/requests/withdraw";
	}

	@PostMapping("/withdraw")
	public String processWithdraw(@PathVariable("requestId") Long requestId,
			@RequestParam(name = "reason", required = false) String reason, Authentication authentication) {
		Integer ownerId = extractOwnerId(authentication);
		this.withdrawalService.withdrawRequest(requestId, ownerId, reason);
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
