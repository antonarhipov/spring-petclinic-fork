package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.interpretation.PromptBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class MyRequestController {

	private final AuthenticatedOwnerService owners;

	private final SchedulingRequestRepository requests;

	private final RequestService requestService;

	private final InterpretationViewMapper viewMapper;

	private final EntityManager entityManager;

	private final PromptBuilder promptBuilder;

	public MyRequestController(AuthenticatedOwnerService owners, SchedulingRequestRepository requests,
			RequestService requestService) {
		this(owners, requests, requestService, new InterpretationViewMapper(null), null, null);
	}

	public MyRequestController(AuthenticatedOwnerService owners, SchedulingRequestRepository requests,
			RequestService requestService, InterpretationViewMapper viewMapper) {
		this(owners, requests, requestService, viewMapper, null, null);
	}

	@Autowired
	public MyRequestController(AuthenticatedOwnerService owners, SchedulingRequestRepository requests,
			RequestService requestService, InterpretationViewMapper viewMapper, EntityManager entityManager,
			PromptBuilder promptBuilder) {
		this.owners = owners;
		this.requests = requests;
		this.requestService = requestService;
		this.viewMapper = viewMapper;
		this.entityManager = entityManager;
		this.promptBuilder = promptBuilder;
	}

	@GetMapping("/my/requests/new")
	String newRequest(@RequestParam(required = false) Integer petId, Principal principal, Model model) {
		Owner owner = owner(principal);
		if (petId != null) {
			this.owners.requirePet(principal, petId);
		}
		model.addAttribute("owner", owner);
		model.addAttribute("selectedPetId", petId);
		return "my/request-form";
	}

	@PostMapping("/my/requests/new")
	String start(@RequestParam int petId, @RequestParam String requestText, Principal principal, Model model) {
		Owner owner = owner(principal);
		this.owners.requirePet(principal, petId);
		RequestService.CreationResult result = this.requestService.startForOwner(petId, requestText);
		if (result.request() == null) {
			model.addAttribute("owner", owner);
			model.addAttribute("selectedPetId", petId);
			model.addAttribute("requestText", requestText);
			model.addAttribute("errorKey", result.messageKey());
			return "my/request-form";
		}
		return "redirect:/my/requests/" + result.request().getId();
	}

	@GetMapping("/my/requests/{id}")
	String request(@PathVariable int id, @RequestParam(required = false) String notice, Principal principal,
			Model model, HttpServletRequest request) {
		if ("slotChanged".equals(notice)) {
			model.addAttribute("actionErrorKey", "scheduling.suggestion.replaced");
		}
		return render(own(id, principal), model, resolveLocale(request));
	}

	@GetMapping("/my/requests/{id}/edit")
	String edit(@PathVariable int id, Principal principal, Model model) {
		model.addAttribute("request", own(id, principal));
		return "my/request-form";
	}

	@PostMapping("/my/requests/{id}/edit")
	String edit(@PathVariable int id, @RequestParam String requestText, Principal principal, Model model,
			HttpServletRequest request) {
		own(id, principal);
		try {
			this.requestService.editText(id, requestText);
			return "redirect:/my/requests/" + id;
		}
		catch (IllegalRequestTransitionException exception) {
			return refused(id, principal, model, "scheduling.request.action.wrong-state", resolveLocale(request));
		}
		catch (IllegalArgumentException exception) {
			return refused(id, principal, model, exception.getMessage(), resolveLocale(request));
		}
	}

	@PostMapping("/my/requests/{id}/consent")
	String consent(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::consent);
	}

	@PostMapping("/my/requests/{id}/decline")
	String decline(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::decline);
	}

	@PostMapping("/my/requests/{id}/confirm")
	String confirm(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::confirmInterpretation);
	}

	@PostMapping("/my/requests/{id}/another-option")
	String anotherOption(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::anotherSuggestion);
	}

	@PostMapping("/my/requests/{id}/accept")
	String accept(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		own(id, principal);
		try {
			SchedulingRequest result = this.requestService.acceptSuggestion(id);
			String notice = result.getState() == RequestState.SUGGESTION_OFFERED ? "?notice=slotChanged" : "";
			return "redirect:/my/requests/" + id + notice;
		}
		catch (IllegalRequestTransitionException exception) {
			return refused(id, principal, model, "scheduling.request.action.wrong-state", resolveLocale(request));
		}
	}

	@PostMapping("/my/requests/{id}/staff-assistance")
	String staffAssistance(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::routeToStaff);
	}

	@PostMapping("/my/requests/{id}/abandon")
	String abandon(@PathVariable int id, Principal principal, Model model, HttpServletRequest request) {
		return action(id, principal, model, resolveLocale(request), this.requestService::abandon);
	}

	private String action(int id, Principal principal, Model model, Locale locale,
			IntFunction<SchedulingRequest> action) {
		own(id, principal);
		try {
			action.apply(id);
			return "redirect:/my/requests/" + id;
		}
		catch (IllegalRequestTransitionException exception) {
			return refused(id, principal, model, "scheduling.request.action.wrong-state", locale);
		}
	}

	private String refused(int id, Principal principal, Model model, String messageKey, Locale locale) {
		model.addAttribute("actionErrorKey", messageKey);
		return render(own(id, principal), model, locale);
	}

	private String render(SchedulingRequest request, Model model) {
		return render(request, model, LocaleContextHolder.getLocale());
	}

	private String render(SchedulingRequest request, Model model, Locale locale) {
		model.addAttribute("request", request);
		model.addAttribute("requestStateMessageKey", requestStateMessageKey(request.getState()));
		if (request.getState() == RequestState.AWAITING_CONSENT && this.promptBuilder != null) {
			model.addAttribute("disclosure", this.promptBuilder.disclosure(request.getCreatedDate()));
		}
		if (request.getState() == RequestState.INTERPRETED || request.getState() == RequestState.WITH_STAFF) {
			model.addAttribute("review", this.viewMapper.toReviewView(request, locale));
		}
		if (request.getState() == RequestState.SUGGESTION_OFFERED && this.entityManager != null) {
			List<Appointment> heldList = this.entityManager.createQuery(
					"select a from Appointment a join fetch a.vet where a.request.id = :requestId and a.status = :status",
					Appointment.class)
				.setParameter("requestId", request.getId())
				.setParameter("status", AppointmentStatus.HELD)
				.getResultList();
			model.addAttribute("held", heldList.isEmpty() ? null : heldList.get(0));
			if (!heldList.isEmpty()) {
				Appointment held = heldList.get(0);
				String specialties = held.getVet()
					.getSpecialties()
					.stream()
					.map(specialty -> specialty.getName())
					.collect(Collectors.joining(", "));
				model.addAttribute("suggestion",
						new SuggestionView(held.getVet().getFirstName() + " " + held.getVet().getLastName(),
								specialties,
								held.getDate()
									.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
								held.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm", locale)),
								held.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm", locale)),
								held.getDurationMinutes(), held.getRankReason()));
			}
		}
		return switch (request.getState()) {
			case AWAITING_CONSENT -> "my/request-consent";
			case INTERPRETING -> "my/request-interpreting";
			case INTERPRETATION_FAILED -> "my/request-failed";
			case INTERPRETED -> "my/request-review";
			case SUGGESTION_OFFERED -> "my/request-suggestion";
			case WITH_STAFF -> "my/request-with-staff";
			case ACCEPTED, ABANDONED -> "my/request-detail";
		};
	}

	public record SuggestionView(String veterinarianName, String specialties, String date, String startTime,
			String endTime, int durationMinutes, String rankReasonMessageKey) {
	}

	private String requestStateMessageKey(RequestState state) {
		return switch (state) {
			case AWAITING_CONSENT -> "scheduling.request.state.awaitingConsent";
			case INTERPRETING -> "scheduling.request.state.interpreting";
			case INTERPRETATION_FAILED -> "scheduling.request.state.interpretationFailed";
			case INTERPRETED -> "scheduling.request.state.interpreted";
			case SUGGESTION_OFFERED -> "scheduling.request.state.suggestionOffered";
			case WITH_STAFF -> "scheduling.request.state.withStaff";
			case ACCEPTED -> "scheduling.request.state.accepted";
			case ABANDONED -> "scheduling.request.state.abandoned";
		};
	}

	private Locale resolveLocale(HttpServletRequest request) {
		if (request == null) {
			return Locale.ENGLISH;
		}
		if (request.getParameter("lang") != null) {
			return RequestContextUtils.getLocale(request);
		}
		Locale requestLocale = request.getLocale();
		if (requestLocale != null && !Locale.ENGLISH.getLanguage().equalsIgnoreCase(requestLocale.getLanguage())) {
			return requestLocale;
		}
		return RequestContextUtils.getLocale(request);
	}

	private SchedulingRequest own(int id, Principal principal) {
		return this.requests.findByIdAndPetOwnerId(id, owner(principal).getId())
			.orElseThrow(OwnerResourceNotFoundException::new);
	}

	private Owner owner(Principal principal) {
		return this.owners.requireOwner(principal);
	}

}
