/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling;

import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.security.UserAccount;
import org.springframework.samples.petclinic.security.UserAccountRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('OWNER')")
@RequestMapping("/my-appointments")
public class OwnerAppointmentController {

	private final BookingService bookingService;

	private final UserAccountRepository userAccountRepository;

	public OwnerAppointmentController(BookingService bookingService, UserAccountRepository userAccountRepository) {
		this.bookingService = bookingService;
		this.userAccountRepository = userAccountRepository;
	}

	private Owner getAuthenticatedOwner(Authentication authentication) {
		Authentication auth = (authentication != null && authentication.isAuthenticated()
				&& !"anonymousUser".equals(authentication.getPrincipal())) ? authentication
						: SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
		}
		UserAccount account = this.userAccountRepository.findByUsername(auth.getName()).orElse(null);
		if (account == null || account.getOwner() == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No owner profile linked to this account");
		}
		return account.getOwner();
	}

	@GetMapping
	public String listMyAppointments(Authentication authentication, Model model) {
		Owner owner = getAuthenticatedOwner(authentication);
		model.addAttribute("scheduleItems", this.bookingService.getScheduleItemsForOwner(owner.getId()));
		return "scheduling/myAppointments";
	}

	@GetMapping("/{appointmentId}")
	public String showMyAppointmentDetails(@PathVariable("appointmentId") Integer appointmentId,
			Authentication authentication, Model model) {
		Owner owner = getAuthenticatedOwner(authentication);
		Appointment appointment = this.bookingService.getAppointment(appointmentId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

		if (!appointment.getOwner().getId().equals(owner.getId())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Access denied: appointment belongs to another owner");
		}

		model.addAttribute("appointment", appointment);
		return "scheduling/myAppointmentDetails";
	}

	@PostMapping("/{appointmentId}/cancel")
	public String cancelMyAppointment(@PathVariable("appointmentId") Integer appointmentId,
			Authentication authentication, RedirectAttributes redirectAttributes) {
		Owner owner = getAuthenticatedOwner(authentication);
		try {
			this.bookingService.cancelByOwner(appointmentId, owner.getId());
			redirectAttributes.addFlashAttribute("message", "Appointment cancelled successfully.");
		}
		catch (ResponseStatusException rse) {
			throw rse;
		}
		catch (Exception ex) {
			redirectAttributes.addFlashAttribute("error", ex.getMessage());
		}
		return "redirect:/my-appointments";
	}

}
