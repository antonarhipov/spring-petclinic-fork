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

package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.RequestLifecycleService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.web.StaffBookingForm;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service orchestrating staff direct booking and open request decision handling (RULE-32,
 * AC-92..95).
 */
@Service
@Transactional
public class StaffBookingService {

	private final AppointmentLifecycleService appointmentLifecycleService;

	private final SchedulingRequestRepository requestRepository;

	private final RequestLifecycleService requestLifecycleService;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final Clock clock;

	public StaffBookingService(AppointmentLifecycleService appointmentLifecycleService,
			SchedulingRequestRepository requestRepository, RequestLifecycleService requestLifecycleService,
			OwnerRepository ownerRepository, VetRepository vetRepository, Clock clock) {
		this.appointmentLifecycleService = appointmentLifecycleService;
		this.requestRepository = requestRepository;
		this.requestLifecycleService = requestLifecycleService;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	public Appointment directBook(StaffBookingForm form, String actor) {
		Objects.requireNonNull(form, "form must not be null");
		Objects.requireNonNull(form.getPetId(), "petId must not be null");
		Objects.requireNonNull(form.getVetId(), "vetId must not be null");
		Objects.requireNonNull(form.getAppointmentDate(), "appointmentDate must not be null");
		Objects.requireNonNull(form.getStartTime(), "startTime must not be null");

		Pet pet = this.ownerRepository.findAll()
			.stream()
			.flatMap(o -> o.getPets().stream())
			.filter(p -> p.getId().equals(form.getPetId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Pet not found: " + form.getPetId()));

		Vet vet = this.vetRepository.findById(form.getVetId())
			.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + form.getVetId()));

		LocalDate date = LocalDate.parse(form.getAppointmentDate());
		LocalTime time = LocalTime.parse(form.getStartTime());
		ZonedDateTime startTime = ZonedDateTime.of(date, time, this.clock.getZone());

		int duration = form.getDurationMinutes() != null ? form.getDurationMinutes() : 30;
		String reason = form.getReason();
		String effectiveActor = (actor != null && !actor.isBlank()) ? actor : "staff";

		SchedulingRequest openRequest = null;
		if (form.getRequestId() != null) {
			openRequest = this.requestRepository.findById(form.getRequestId()).orElse(null);
		}
		if (openRequest == null && pet.getId() != null) {
			openRequest = this.requestRepository.findByActivePetId(pet.getId()).orElse(null);
		}

		if (openRequest != null && !openRequest.getState().isTerminal()) {
			String decision = form.getDecision() != null ? form.getDecision().trim() : "attach";
			if ("attach".equalsIgnoreCase(decision)) {
				Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, startTime,
						duration, reason, openRequest, effectiveActor);
				this.requestLifecycleService.staffBookAttach(openRequest, effectiveActor,
						"staff direct booking attach");
				return appointment;
			}
			else {
				Appointment appointment = this.appointmentLifecycleService.bookAppointment(pet, vet, startTime,
						duration, reason, null, effectiveActor);
				this.requestLifecycleService.staffBookLeaveOpen(openRequest, effectiveActor,
						"staff direct booking leave open");
				return appointment;
			}
		}

		return this.appointmentLifecycleService.bookAppointment(pet, vet, startTime, duration, reason, null,
				effectiveActor);
	}

}
