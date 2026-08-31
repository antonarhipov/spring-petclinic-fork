package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.availability.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.queue.AssistedOfferService.AssistedOfferCommand;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.AssistedOfferForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.CloseQueueForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueActionForms.QueueDirectBookForm;
import org.springframework.samples.petclinic.scheduling.queue.QueueDirectBookingService.QueueDirectBookCommand;
import org.springframework.samples.petclinic.security.PetClinicPrincipal;
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
@RequestMapping("/staff/queue")
public class StaffQueueResolutionController {

	private final StaffQueueQueryService queryService;

	private final AssistedOfferService assistedOfferService;

	private final QueueDirectBookingService queueDirectBookingService;

	private final QueueContactService queueContactService;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final VetRepository vetRepository;

	public StaffQueueResolutionController(StaffQueueQueryService queryService,
			AssistedOfferService assistedOfferService, QueueDirectBookingService queueDirectBookingService,
			QueueContactService queueContactService, EffectiveAvailabilityService effectiveAvailabilityService,
			VetRepository vetRepository) {
		this.queryService = queryService;
		this.assistedOfferService = assistedOfferService;
		this.queueDirectBookingService = queueDirectBookingService;
		this.queueContactService = queueContactService;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.vetRepository = vetRepository;
	}

	@GetMapping("/{id}/offer")
	public String offerForm(@PathVariable("id") Long id, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		AssistedOfferForm form = new AssistedOfferForm();
		form.setVetId(detail.preferredVetId());
		form.setDurationMinutes(detail.durationMinutes() != null ? detail.durationMinutes() : 30);

		populateReferenceData(model);
		model.addAttribute("queueItem", detail);
		model.addAttribute("offerForm", form);
		return "staff/queue/offer";
	}

	@PostMapping("/{id}/offer")
	public String submitOffer(@PathVariable("id") Long id, @ModelAttribute("offerForm") AssistedOfferForm form,
			Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
		Long actorId = extractActorId(authentication);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();

		if (form.getDate() == null || form.getStartTime() == null || form.getVetId() == null) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			populateReferenceData(model);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", "Veterinarian, date, and start time are required.");
			return "staff/queue/offer";
		}

		Instant startAt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId).toInstant();
		Instant endAt = startAt
			.plus(Duration.ofMinutes(form.getDurationMinutes() != null ? form.getDurationMinutes() : 30));

		AssistedOfferCommand cmd = new AssistedOfferCommand(id, actorId, form.getVetId(), startAt, endAt,
				form.getExplanation());

		try {
			Offer offer = this.assistedOfferService.createAssistedOffer(cmd);
			redirectAttributes.addFlashAttribute("successMessage",
					"Assisted offer created and held until " + offer.getExpiresAt());
			return "redirect:/staff/queue/" + id;
		}
		catch (Exception ex) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			populateReferenceData(model);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/queue/offer";
		}
	}

	@GetMapping("/{id}/direct-book")
	public String directBookForm(@PathVariable("id") Long id, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		QueueDirectBookForm form = new QueueDirectBookForm();
		form.setVetId(detail.preferredVetId());
		form.setDurationMinutes(detail.durationMinutes() != null ? detail.durationMinutes() : 30);

		populateReferenceData(model);
		model.addAttribute("queueItem", detail);
		model.addAttribute("directBookForm", form);
		return "staff/queue/direct-book";
	}

	@PostMapping("/{id}/direct-book")
	public String submitDirectBook(@PathVariable("id") Long id,
			@ModelAttribute("directBookForm") QueueDirectBookForm form, Authentication authentication,
			RedirectAttributes redirectAttributes, Model model) {
		Long actorId = extractActorId(authentication);
		ZoneId zoneId = this.effectiveAvailabilityService.getClinicZoneId();

		if (form.getDate() == null || form.getStartTime() == null || form.getVetId() == null
				|| form.getInternalReason() == null || form.getInternalReason().isBlank()) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			populateReferenceData(model);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", "Veterinarian, date, start time, and internal reason are required.");
			return "staff/queue/direct-book";
		}

		Instant startAt = ZonedDateTime.of(form.getDate(), form.getStartTime(), zoneId).toInstant();
		Instant endAt = startAt
			.plus(Duration.ofMinutes(form.getDurationMinutes() != null ? form.getDurationMinutes() : 30));

		QueueDirectBookCommand cmd = new QueueDirectBookCommand(id, actorId, form.getVetId(), startAt, endAt,
				form.isOwnerAgreementRecorded(), form.getAgreementMedium(), form.getInternalReason());

		try {
			Appointment appointment = this.queueDirectBookingService.directBookFromQueue(cmd);
			redirectAttributes.addFlashAttribute("successMessage", "Appointment booked directly. Queue item resolved.");
			return "redirect:/staff/appointments/" + appointment.getId();
		}
		catch (Exception ex) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			populateReferenceData(model);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/queue/direct-book";
		}
	}

	@GetMapping("/{id}/close")
	public String closeForm(@PathVariable("id") Long id, Model model) {
		StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + id));

		model.addAttribute("queueItem", detail);
		model.addAttribute("closeQueueForm", new CloseQueueForm());
		return "staff/queue/close";
	}

	@PostMapping("/{id}/close")
	public String submitClose(@PathVariable("id") Long id, @ModelAttribute("closeQueueForm") CloseQueueForm form,
			Authentication authentication, RedirectAttributes redirectAttributes, Model model) {
		Long actorId = extractActorId(authentication);

		if (form.getReason() == null || form.getReason().isBlank()) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", "Closure reason is required.");
			return "staff/queue/close";
		}

		try {
			this.queueContactService.closeQueueItem(id, actorId, form.getReason());
			redirectAttributes.addFlashAttribute("successMessage", "Queue item closed.");
			return "redirect:/staff/queue";
		}
		catch (Exception ex) {
			StaffQueueQueryService.QueueItemDetailDto detail = this.queryService.getQueueItemDetail(id).orElse(null);
			model.addAttribute("queueItem", detail);
			model.addAttribute("errorMessage", ex.getMessage());
			return "staff/queue/close";
		}
	}

	private void populateReferenceData(Model model) {
		model.addAttribute("allVets", this.vetRepository.findAll());
		model.addAttribute("allowedDurations",
				this.effectiveAvailabilityService.getClinicPolicy().getAllowedDurations());
	}

	private Long extractActorId(Authentication authentication) {
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			return principal.getAccountId();
		}
		return 1L;
	}

}
