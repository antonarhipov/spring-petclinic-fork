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

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Appointment-management boundary for staff actions and their audit trail. */
@Service
public class AppointmentManagementService {

	private final AppointmentLifecycleService lifecycleService;

	private final AppointmentRepository appointmentRepository;

	private final AppointmentChangeRepository changeRepository;

	private final VetRepository vetRepository;

	public AppointmentManagementService(AppointmentLifecycleService lifecycleService,
			AppointmentRepository appointmentRepository, AppointmentChangeRepository changeRepository,
			VetRepository vetRepository) {
		this.lifecycleService = lifecycleService;
		this.appointmentRepository = appointmentRepository;
		this.changeRepository = changeRepository;
		this.vetRepository = vetRepository;
	}

	@Transactional(readOnly = true)
	public Appointment requireAppointment(Integer appointmentId) {
		return this.appointmentRepository.findDetailedById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));
	}

	@Transactional(readOnly = true)
	public Vet requireVet(Integer vetId) {
		return this.vetRepository.findById(vetId)
			.orElseThrow(() -> new IllegalArgumentException("Veterinarian not found: " + vetId));
	}

	@Transactional(readOnly = true)
	public Collection<Vet> allVets() {
		return this.vetRepository.findAll();
	}

	public Appointment staffReschedule(Appointment appointment, String actor, String reason, ZonedDateTime newStartTime,
			int newDuration, Vet newVet) {
		return this.lifecycleService.staffReschedule(appointment, actor, reason, newStartTime, newDuration, newVet);
	}

	public Appointment staffCancel(Appointment appointment, String actor, String reason) {
		return this.lifecycleService.staffCancel(appointment, actor, reason);
	}

	@Transactional(readOnly = true)
	public Map<Integer, AppointmentChange> latestChanges(Collection<Integer> appointmentIds) {
		Map<Integer, AppointmentChange> changes = new LinkedHashMap<>();
		for (Integer appointmentId : appointmentIds) {
			this.changeRepository.findTopByAppointmentIdOrderByTimestampDescIdDesc(appointmentId)
				.filter(change -> change.getReason() != null && !change.getReason().isBlank())
				.ifPresent(change -> changes.put(appointmentId, change));
		}
		return changes;
	}

	@Transactional(readOnly = true)
	public List<AppointmentChange> changes(Integer appointmentId) {
		return this.changeRepository.findByAppointmentIdOrderByTimestampAsc(appointmentId);
	}

}
