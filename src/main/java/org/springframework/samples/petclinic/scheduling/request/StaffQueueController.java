package org.springframework.samples.petclinic.scheduling.request;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.RequestContextUtils;

import jakarta.servlet.http.HttpServletRequest;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
public class StaffQueueController {

	private final StaffQueueQueryService queryService;

	public StaffQueueController(StaffQueueQueryService queryService) {
		this.queryService = queryService;
	}

	@GetMapping("/staff/queue")
	String queue(Model model) {
		model.addAttribute("queue", this.queryService.queue());
		return "staff/queue";
	}

	@GetMapping("/staff/requests/{id}")
	String detail(@PathVariable int id, HttpServletRequest request, Model model) {
		StaffQueueQueryService.RequestDetail detail = this.queryService.detail(id, resolveLocale(request))
			.orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
		model.addAttribute("detail", detail);
		model.addAttribute("interpretationForm", StaffInterpretationForm.from(detail.current()));
		return "staff/request-detail";
	}

	private Locale resolveLocale(HttpServletRequest request) {
		if (request.getParameter("lang") != null) {
			return RequestContextUtils.getLocale(request);
		}
		Locale requestLocale = request.getLocale();
		if (requestLocale != null && !Locale.ENGLISH.getLanguage().equalsIgnoreCase(requestLocale.getLanguage())) {
			return requestLocale;
		}
		return RequestContextUtils.getLocale(request);
	}

}
