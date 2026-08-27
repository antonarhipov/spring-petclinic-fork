/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.staff;

import java.util.List;

import org.springframework.samples.petclinic.appointment.Appointment;
import org.springframework.samples.petclinic.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.appointment.AppointmentStatus;
import org.springframework.samples.petclinic.calendar.ClinicSettingsRepository;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentLifecycleService {

	private final AppointmentRepository appointmentRepository;

	private final OwnerRepository ownerRepository;

	private final ClinicSettingsRepository settingsRepository;

	public AppointmentLifecycleService(AppointmentRepository appointmentRepository, OwnerRepository ownerRepository,
			ClinicSettingsRepository settingsRepository) {
		this.appointmentRepository = appointmentRepository;
		this.ownerRepository = ownerRepository;
		this.settingsRepository = settingsRepository;
	}

	@Transactional(readOnly = true)
	public List<Appointment> getScheduledAppointments() {
		return this.appointmentRepository.findByStatus(AppointmentStatus.SCHEDULED);
	}

	@Transactional
	public void cancel(Integer appointmentId, String reason) {
		Appointment appointment = getScheduledForUpdate(appointmentId);
		appointment.setReason(requireReason(reason));
		appointment.setStatus(AppointmentStatus.CANCELLED);
	}

	@Transactional
	public void markNoShow(Integer appointmentId) {
		Appointment appointment = getScheduledForUpdate(appointmentId);
		appointment.setStatus(AppointmentStatus.NO_SHOW);
	}

	@Transactional
	public void complete(Integer appointmentId) {
		Appointment appointment = getScheduledForUpdate(appointmentId);
		Owner owner = this.ownerRepository.findByPetId(appointment.getPet().getId())
			.orElseThrow(() -> new IllegalStateException("Owner not found for appointment pet"));

		Visit visit = new Visit();
		visit.setDate(appointment.getStartInstant()
			.atZone(this.settingsRepository.getClinicSettings().getZone())
			.toLocalDate());
		String description = appointment.getReason();
		if (description == null || description.isBlank()) {
			description = "Completed appointment with " + appointment.getVet().getFirstName() + " "
					+ appointment.getVet().getLastName();
		}
		visit.setDescription(description);
		owner.addVisit(appointment.getPet().getId(), visit);
		this.ownerRepository.save(owner);
		appointment.setStatus(AppointmentStatus.COMPLETED);
	}

	private Appointment getScheduledForUpdate(Integer appointmentId) {
		Appointment appointment = this.appointmentRepository.findByIdForUpdate(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + appointmentId));
		if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
			throw new IllegalStateException("Only scheduled appointments can change lifecycle state");
		}
		return appointment;
	}

	static String requireReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A reason is required");
		}
		return reason.trim();
	}

}
