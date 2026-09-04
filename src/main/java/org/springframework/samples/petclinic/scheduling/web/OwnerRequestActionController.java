/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigService;
import org.springframework.samples.petclinic.scheduling.request.IllegalRequestTransitionException;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SuggestionService;
import org.springframework.samples.petclinic.security.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HTTP surface for owner actions on an existing scheduling request. */
@Controller
@RequestMapping("/my/requests/{requestId}")
public class OwnerRequestActionController {

	static final String REQUEST_EDITED = "requestEdited";

	static final String REQUEST_ABANDONED = "requestAbandoned";

	static final String REQUEST_ROUTED_TO_STAFF = "requestRoutedToStaff";

	private final OwnerSchedulingAccessService accessService;

	private final RequestLifecycleService lifecycleService;

	private final SuggestionService suggestionService;

	private final ClinicConfigService clinicConfigService;

	public OwnerRequestActionController(OwnerSchedulingAccessService accessService,
			RequestLifecycleService lifecycleService, SuggestionService suggestionService,
			ClinicConfigService clinicConfigService) {
		this.accessService = accessService;
		this.lifecycleService = lifecycleService;
		this.suggestionService = suggestionService;
		this.clinicConfigService = clinicConfigService;
	}

	@GetMapping("/edit")
	public String edit(@PathVariable Integer requestId, Model model) {
		SchedulingRequest request = requireRequest(requestId);
		model.addAttribute("request", request);
		model.addAttribute("editForm", OwnerRequestEditForm.from(request));
		model.addAttribute("editing", true);
		model.addAttribute("emergencyPhone", this.clinicConfigService.current().getEmergencyPhone());
		return "my/requestDetail";
	}

	@PostMapping("/edit")
	public String edit(@PathVariable Integer requestId, @ModelAttribute("editForm") OwnerRequestEditForm form,
			RedirectAttributes redirectAttributes) {
		SchedulingRequest request = requireRequest(requestId);
		try {
			this.lifecycleService.editText(request, actor(), form.getReasonText(), form.getAvailabilityText());
		}
		catch (IllegalRequestTransitionException ex) {
			return refused(requestId, redirectAttributes);
		}
		redirectAttributes.addFlashAttribute(REQUEST_EDITED, true);
		return detailRedirect(requestId);
	}

	@PostMapping("/abandon")
	public String abandon(@PathVariable Integer requestId,
			@RequestParam(defaultValue = "Owner abandoned request") String reason,
			RedirectAttributes redirectAttributes) {
		SchedulingRequest request = requireRequest(requestId);
		try {
			this.lifecycleService.abandon(request, actor(), reason);
		}
		catch (IllegalRequestTransitionException ex) {
			return refused(requestId, redirectAttributes);
		}
		redirectAttributes.addFlashAttribute(REQUEST_ABANDONED, true);
		return detailRedirect(requestId);
	}

	@PostMapping("/route-to-staff")
	public String routeToStaff(@PathVariable Integer requestId,
			@RequestParam(defaultValue = "Owner requested staff help") String reason,
			RedirectAttributes redirectAttributes) {
		SchedulingRequest request = requireRequest(requestId);
		try {
			this.lifecycleService.routeToStaff(request, actor(), reason);
		}
		catch (IllegalRequestTransitionException ex) {
			return refused(requestId, redirectAttributes);
		}
		redirectAttributes.addFlashAttribute(REQUEST_ROUTED_TO_STAFF, true);
		return detailRedirect(requestId);
	}

	@PostMapping("/another")
	public String another(@PathVariable Integer requestId, @RequestParam(defaultValue = "NOT_THIS_TIME") String scope,
			RedirectAttributes redirectAttributes) {
		SchedulingRequest request = requireRequest(requestId);
		try {
			this.suggestionService.requestAnotherOption(request, actor(), scope);
		}
		catch (IllegalRequestTransitionException ex) {
			return refused(requestId, redirectAttributes);
		}
		return detailRedirect(requestId);
	}

	private SchedulingRequest requireRequest(Integer requestId) {
		Integer ownerId = SecurityUtils.getCurrentOwnerId().orElseThrow(OwnerResourceNotFoundException::new);
		return this.accessService.requireRequest(ownerId, requestId);
	}

	private static String actor() {
		return SecurityUtils.getCurrentUsername().orElseThrow();
	}

	private static String refused(Integer requestId, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute(OwnerRequestController.ACTION_NOT_ALLOWED, true);
		return detailRedirect(requestId);
	}

	private static String detailRedirect(Integer requestId) {
		return "redirect:/my/requests/" + requestId;
	}

}
