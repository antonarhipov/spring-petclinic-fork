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

import java.time.Clock;
import java.time.ZonedDateTime;

import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentChange;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentLifecycleService;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.appointment.IllegalAppointmentTransitionException;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigService;
import org.springframework.samples.petclinic.security.SecurityUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/my/appointments")
public class OwnerAppointmentController {

	private final OwnerSchedulingAccessService accessService;

	private final AppointmentLifecycleService appointmentLifecycleService;

	private final ClinicConfigService clinicConfigService;

	private final Clock clock;

	public OwnerAppointmentController(OwnerSchedulingAccessService accessService,
			AppointmentLifecycleService appointmentLifecycleService, ClinicConfigService clinicConfigService,
			Clock clock) {
		this.accessService = accessService;
		this.appointmentLifecycleService = appointmentLifecycleService;
		this.clinicConfigService = clinicConfigService;
		this.clock = clock;
	}

	@GetMapping("/{appointmentId}")
	public String appointmentDetail(@PathVariable Integer appointmentId, Model model) {
		Integer ownerId = SecurityUtils.getCurrentOwnerId().orElseThrow(OwnerResourceNotFoundException::new);
		Appointment appointment = this.accessService.requireAppointment(ownerId, appointmentId);
		AppointmentChange latestStaffChange = this.accessService.findLatestStaffChange(ownerId, appointmentId);
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		boolean canCancel = appointment.getStatus() == AppointmentStatus.CONFIRMED
				&& !appointment.getStartTime().isBefore(now);

		model.addAttribute("appointment", appointment);
		model.addAttribute("latestStaffChange", latestStaffChange);
		model.addAttribute("canCancel", canCancel);
		model.addAttribute("emergencyPhone", this.clinicConfigService.current().getEmergencyPhone());
		return "my/appointmentDetail";
	}

	@PostMapping("/{appointmentId}/cancel")
	public String cancelAppointment(@PathVariable Integer appointmentId, RedirectAttributes redirectAttributes) {
		Integer ownerId = SecurityUtils.getCurrentOwnerId().orElseThrow(OwnerResourceNotFoundException::new);
		Appointment appointment = this.accessService.requireAppointment(ownerId, appointmentId);
		String username = SecurityUtils.getCurrentUsername().orElseThrow(OwnerResourceNotFoundException::new);

		try {
			this.appointmentLifecycleService.ownerCancel(appointment, username);
			redirectAttributes.addFlashAttribute("appointmentCancelled", true);
		}
		catch (IllegalAppointmentTransitionException ex) {
			redirectAttributes.addFlashAttribute("appointmentActionNotAllowed", true);
			return "redirect:/my/appointments/" + appointmentId;
		}

		return "redirect:/my/appointments";
	}

}
