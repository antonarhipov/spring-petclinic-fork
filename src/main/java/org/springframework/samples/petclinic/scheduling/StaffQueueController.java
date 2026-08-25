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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.QueueReason;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasRole('STAFF')")
@RequestMapping("/staff/queue")
public class StaffQueueController {

	private final SchedulingRequestRepository schedulingRequests;

	private final BookingService bookingService;

	private final VetRepository vets;

	public StaffQueueController(SchedulingRequestRepository schedulingRequests, BookingService bookingService,
			VetRepository vets) {
		this.schedulingRequests = schedulingRequests;
		this.bookingService = bookingService;
		this.vets = vets;
	}

	@GetMapping
	public String listQueue(Model model) {
		List<SchedulingRequest> queued = this.schedulingRequests.findAll()
			.stream()
			.filter(r -> r.getState() == RequestState.STAFF_QUEUED)
			.sorted((a, b) -> {
				boolean aEmerg = a.getQueueReason() == QueueReason.EMERGENCY;
				boolean bEmerg = b.getQueueReason() == QueueReason.EMERGENCY;
				if (aEmerg != bEmerg) {
					return aEmerg ? -1 : 1;
				}
				Instant aTime = a.getQueuedAt() != null ? a.getQueuedAt() : Instant.EPOCH;
				Instant bTime = b.getQueuedAt() != null ? b.getQueuedAt() : Instant.EPOCH;
				return aTime.compareTo(bTime);
			})
			.toList();
		model.addAttribute("requests", queued);
		return "scheduling/queueList";
	}

	@GetMapping("/{requestId}")
	public String showQueueDetails(@PathVariable("requestId") Integer requestId, Model model) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
		Collection<Vet> vetList = this.vets.findAll();
		model.addAttribute("request", request);
		model.addAttribute("vets", vetList);
		model.addAttribute("selectedVetId", null);
		model.addAttribute("error", null);
		model.addAttribute("defaultDate", LocalDate.now().plusDays(1));
		model.addAttribute("defaultTime", LocalTime.of(9, 0));
		return "scheduling/queueDetails";
	}

	@PostMapping("/{requestId}/book")
	public String bookOnBehalf(@PathVariable("requestId") Integer requestId, @RequestParam("vetId") Integer vetId,
			@RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam(value = "durationMinutes", defaultValue = "30") int durationMinutes,
			@RequestParam(value = "reason", required = false) String reason, Model model,
			RedirectAttributes redirectAttributes) {
		try {
			LocalDateTime start = LocalDateTime.of(date, startTime);
			LocalDateTime end = start.plusMinutes(durationMinutes);
			Appointment appt = this.bookingService.bookOnBehalf(requestId, vetId, start, end, reason);
			redirectAttributes.addFlashAttribute("message", "Appointment successfully booked on owner's behalf.");
			return "redirect:/staff/appointments/" + appt.getId();
		}
		catch (Exception ex) {
			SchedulingRequest request = this.schedulingRequests.findById(requestId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
			model.addAttribute("request", request);
			model.addAttribute("vets", this.vets.findAll());
			model.addAttribute("error", ex.getMessage());
			model.addAttribute("selectedVetId", vetId);
			model.addAttribute("date", date);
			model.addAttribute("startTime", startTime);
			model.addAttribute("durationMinutes", durationMinutes);
			model.addAttribute("reason", reason);
			return "scheduling/queueDetails";
		}
	}

}
