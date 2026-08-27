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
package org.springframework.samples.petclinic.staff;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.calendar.ClinicSettings;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.calendar.GridGenerator;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service providing staff direct booking functionality against the deterministic
 * availability grid with collision filtering.
 */
@Service
public class StaffBookingService {

	private final ClinicSettingsRepository clinicSettingsRepository;

	private final GridGenerator gridGenerator;

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	private final AppointmentRequestWorkflowService workflowService;

	public StaffBookingService(ClinicSettingsRepository clinicSettingsRepository, GridGenerator gridGenerator,
			AppointmentRepository appointmentRepository, OwnerRepository ownerRepository, VetRepository vetRepository,
			AppointmentRequestWorkflowService workflowService) {
		this.clinicSettingsRepository = clinicSettingsRepository;
		this.gridGenerator = gridGenerator;
		this.appointmentRepository = appointmentRepository;
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
		this.workflowService = workflowService;
	}

	/**
	 * Retrieves all available start instants for a veterinarian on a given date for the
	 * specified visit duration, filtering out any candidate slots that overlap with
	 * existing non-cancelled appointments.
	 */
	@Transactional(readOnly = true)
	public List<Instant> getAvailableSlots(Integer vetId, LocalDate date, Integer durationMin) {
		return getAvailableSlotsExcluding(vetId, date, durationMin, null);
	}

	@Transactional(readOnly = true)
	public List<Instant> getAvailableSlotsExcluding(Integer vetId, LocalDate date, Integer durationMin,
			Integer excludedAppointmentId) {
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(date, "date must not be null");

		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		int duration = settings.clampDuration(durationMin);
		ZoneId zoneId = settings.getZone();

		LocalDate today = LocalDate.now(zoneId);
		LocalDate horizonEnd = today.plusDays(settings.getBookingHorizonDays());
		if (date.isBefore(today) || !date.isBefore(horizonEnd)) {
			return List.of();
		}

		List<Instant> candidateStarts = this.gridGenerator.generateCandidateStarts(vetId, date, duration, settings);
		if (candidateStarts.isEmpty()) {
			return List.of();
		}

		List<Appointment> existingAppointments = this.appointmentRepository.findByVetIdAndStatusNot(vetId,
				AppointmentStatus.CANCELLED);

		List<Instant> availableStarts = new ArrayList<>();
		for (Instant start : candidateStarts) {
			boolean collision = false;
			for (Appointment existing : existingAppointments) {
				if (!Objects.equals(existing.getId(), excludedAppointmentId) && existing.overlaps(start, duration)) {
					collision = true;
					break;
				}
			}
			if (!collision) {
				availableStarts.add(start);
			}
		}

		return Collections.unmodifiableList(availableStarts);
	}

	/**
	 * Books an appointment directly by staff with no request link, validating
	 * availability and collisions.
	 */
	@Transactional
	public Appointment bookDirectAppointment(Integer ownerId, Integer petId, Integer vetId, Instant startInstant,
			Integer durationMin, String reason) {
		return bookDirectAppointment(ownerId, petId, vetId, startInstant, durationMin, reason, null);
	}

	@Transactional
	public Appointment bookDirectAppointment(Integer ownerId, Integer petId, Integer vetId, Instant startInstant,
			Integer durationMin, String reason, Integer requestId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(petId, "petId must not be null");
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(startInstant, "startInstant must not be null");

		Owner owner = this.ownerRepository.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet with id " + petId + " not found for owner with id " + ownerId);
		}

		Vet vet = this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));

		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		int duration = settings.clampDuration(durationMin);
		LocalDate date = startInstant.atZone(settings.getZone()).toLocalDate();

		List<Instant> availableSlots = getAvailableSlots(vetId, date, duration);
		if (!availableSlots.contains(startInstant)) {
			throw new IllegalStateException(
					"The selected slot is no longer available or overlaps with an existing appointment.");
		}

		Appointment appointment = new Appointment();
		appointment.setRequest(null);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setStartInstant(startInstant);
		appointment.setDurationMin(duration);
		appointment.setStatus(AppointmentStatus.SCHEDULED);
		appointment
			.setReason(requestId == null ? trimToNull(reason) : AppointmentLifecycleService.requireReason(reason));

		Appointment saved = this.appointmentRepository.saveAndFlush(appointment);
		if (requestId != null) {
			this.workflowService.completeByStaff(requestId, saved);
		}
		return saved;
	}

	@Transactional
	public Appointment reschedule(Integer appointmentId, Integer vetId, Instant startInstant, Integer durationMin,
			String reason) {
		Objects.requireNonNull(appointmentId, "appointmentId must not be null");
		Objects.requireNonNull(vetId, "vetId must not be null");
		Objects.requireNonNull(startInstant, "startInstant must not be null");
		Appointment appointment = this.appointmentRepository.findByIdForUpdate(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
			throw new IllegalStateException("Only scheduled appointments can be rescheduled");
		}

		Vet vet = this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found with id: " + vetId));
		ClinicSettings settings = this.clinicSettingsRepository.getClinicSettings();
		int duration = settings.clampDuration(durationMin);
		LocalDate date = startInstant.atZone(settings.getZone()).toLocalDate();
		if (!getAvailableSlotsExcluding(vetId, date, duration, appointmentId).contains(startInstant)) {
			throw new IllegalStateException(
					"The selected slot is no longer available or overlaps with an existing appointment.");
		}
		appointment.setVet(vet);
		appointment.setStartInstant(startInstant);
		appointment.setDurationMin(duration);
		appointment.setReason(AppointmentLifecycleService.requireReason(reason));
		return appointment;
	}

	private static String trimToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
