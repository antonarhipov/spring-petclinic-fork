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
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.samples.petclinic.clinic.AvailabilityService;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.owner.Visit;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.Hold;
import org.springframework.samples.petclinic.scheduling.model.HoldRepository;
import org.springframework.samples.petclinic.scheduling.model.HoldStatus;
import org.springframework.samples.petclinic.scheduling.model.OccupancyType;
import org.springframework.samples.petclinic.scheduling.model.RequestState;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancy;
import org.springframework.samples.petclinic.scheduling.model.SlotOccupancyRepository;
import org.springframework.samples.petclinic.scheduling.solver.VetAvailability;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

	private final AppointmentRepository appointments;

	private final HoldRepository holds;

	private final SlotOccupancyRepository slotOccupancies;

	private final SchedulingRequestRepository schedulingRequests;

	private final HoldService holdService;

	private final AvailabilityService availabilityService;

	private final OwnerRepository owners;

	private final VetRepository vets;

	public BookingService(AppointmentRepository appointments, HoldRepository holds,
			SlotOccupancyRepository slotOccupancies, SchedulingRequestRepository schedulingRequests,
			HoldService holdService, AvailabilityService availabilityService, OwnerRepository owners,
			VetRepository vets) {
		this.appointments = appointments;
		this.holds = holds;
		this.slotOccupancies = slotOccupancies;
		this.schedulingRequests = schedulingRequests;
		this.holdService = holdService;
		this.availabilityService = availabilityService;
		this.owners = owners;
		this.vets = vets;
	}

	@Transactional
	public Appointment acceptHold(Integer requestId) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));

		Optional<Hold> activeHoldOpt = this.holds.findBySchedulingRequestIdAndStatus(requestId, HoldStatus.ACTIVE);
		if (activeHoldOpt.isEmpty()) {
			throw new IllegalStateException("No active hold found for request " + requestId);
		}

		Hold hold = activeHoldOpt.get();
		if (hold.isExpired(Instant.now())) {
			this.holdService.expireHold(hold);
			request.transitionTo(RequestState.SUGGESTING, null);
			this.schedulingRequests.saveAndFlush(request);
			throw new IllegalStateException("Hold has expired");
		}

		Appointment appointment = new Appointment();
		appointment.setOwner(request.getOwner());
		appointment.setPet(request.getPet());
		appointment.setVet(hold.getVet());
		appointment.setSchedulingRequest(request);
		appointment.setStartTime(hold.getStartTime());
		appointment.setEndTime(hold.getEndTime());
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setReason(request.getRawText());
		Appointment savedAppointment = this.appointments.saveAndFlush(appointment);

		Optional<SlotOccupancy> occupancyOpt = this.slotOccupancies.findByVetIdAndStartTime(hold.getVet().getId(),
				hold.getStartTime());
		if (occupancyOpt.isPresent()) {
			SlotOccupancy occupancy = occupancyOpt.get();
			occupancy.setOccupancyType(OccupancyType.APPOINTMENT);
			occupancy.setReferenceId(savedAppointment.getId());
			this.slotOccupancies.saveAndFlush(occupancy);
		}

		hold.setStatus(HoldStatus.CONSUMED);
		this.holds.saveAndFlush(hold);

		request.transitionTo(RequestState.CONFIRMED, null);
		this.schedulingRequests.saveAndFlush(request);

		return savedAppointment;
	}

	@Transactional
	public Appointment bookDirect(Integer ownerId, Integer petId, Integer vetId, LocalDateTime startTime,
			LocalDateTime endTime, String reason) {
		Owner owner = this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found: " + ownerId));
		Pet pet = owner.getPet(petId);
		if (pet == null) {
			throw new IllegalArgumentException("Pet " + petId + " does not belong to owner " + ownerId);
		}
		Vet vet = this.vets.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));

		validateSlot(vetId, startTime, endTime, null);

		Appointment appointment = new Appointment();
		appointment.setOwner(owner);
		appointment.setPet(pet);
		appointment.setVet(vet);
		appointment.setSchedulingRequest(null);
		appointment.setStartTime(startTime);
		appointment.setEndTime(endTime);
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setReason(reason);
		Appointment saved = this.appointments.saveAndFlush(appointment);

		SlotOccupancy occupancy = new SlotOccupancy();
		occupancy.setVet(vet);
		occupancy.setStartTime(startTime);
		occupancy.setEndTime(endTime);
		occupancy.setOccupancyType(OccupancyType.APPOINTMENT);
		occupancy.setReferenceId(saved.getId());
		this.slotOccupancies.saveAndFlush(occupancy);

		return saved;
	}

	@Transactional
	public Appointment bookOnBehalf(Integer requestId, Integer vetId, LocalDateTime startTime, LocalDateTime endTime,
			String reason) {
		SchedulingRequest request = this.schedulingRequests.findById(requestId)
			.orElseThrow(() -> new IllegalArgumentException("SchedulingRequest not found: " + requestId));
		Vet vet = this.vets.findById(vetId).orElseThrow(() -> new IllegalArgumentException("Vet not found: " + vetId));

		validateSlot(vetId, startTime, endTime, null);

		Appointment appointment = new Appointment();
		appointment.setOwner(request.getOwner());
		appointment.setPet(request.getPet());
		appointment.setVet(vet);
		appointment.setSchedulingRequest(request);
		appointment.setStartTime(startTime);
		appointment.setEndTime(endTime);
		appointment.setStatus(AppointmentStatus.BOOKED);
		appointment.setReason((reason != null && !reason.isBlank()) ? reason : request.getRawText());
		Appointment saved = this.appointments.saveAndFlush(appointment);

		SlotOccupancy occupancy = new SlotOccupancy();
		occupancy.setVet(vet);
		occupancy.setStartTime(startTime);
		occupancy.setEndTime(endTime);
		occupancy.setOccupancyType(OccupancyType.APPOINTMENT);
		occupancy.setReferenceId(saved.getId());
		this.slotOccupancies.saveAndFlush(occupancy);

		// If there was an active hold on this request, release it
		Optional<Hold> activeHold = this.holds.findBySchedulingRequestIdAndStatus(requestId, HoldStatus.ACTIVE);
		activeHold.ifPresent(h -> {
			h.setStatus(HoldStatus.CONSUMED);
			this.holds.saveAndFlush(h);
		});

		request.transitionTo(RequestState.CONFIRMED, null);
		this.schedulingRequests.saveAndFlush(request);

		return saved;
	}

	@Transactional
	public Appointment rescheduleAppointment(Integer appointmentId, Integer newVetId, LocalDateTime newStartTime,
			LocalDateTime newEndTime, String changeReason) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Only BOOKED appointments can be rescheduled");
		}
		if (changeReason == null || changeReason.isBlank()) {
			throw new IllegalArgumentException("Change reason is required for rescheduling");
		}

		Vet newVet = this.vets.findById(newVetId)
			.orElseThrow(() -> new IllegalArgumentException("Vet not found: " + newVetId));

		Optional<SlotOccupancy> currentOccupancy = this.slotOccupancies
			.findByOccupancyTypeAndReferenceId(OccupancyType.APPOINTMENT, appointment.getId());
		Integer excludeOccupancyId = currentOccupancy.map(SlotOccupancy::getId).orElse(null);

		validateSlot(newVetId, newStartTime, newEndTime, excludeOccupancyId);

		if (currentOccupancy.isPresent()) {
			SlotOccupancy occ = currentOccupancy.get();
			occ.setVet(newVet);
			occ.setStartTime(newStartTime);
			occ.setEndTime(newEndTime);
			this.slotOccupancies.saveAndFlush(occ);
		}
		else {
			this.slotOccupancies.deleteByVetIdAndStartTime(appointment.getVet().getId(), appointment.getStartTime());
			SlotOccupancy occ = new SlotOccupancy();
			occ.setVet(newVet);
			occ.setStartTime(newStartTime);
			occ.setEndTime(newEndTime);
			occ.setOccupancyType(OccupancyType.APPOINTMENT);
			occ.setReferenceId(appointment.getId());
			this.slotOccupancies.saveAndFlush(occ);
		}

		appointment.setVet(newVet);
		appointment.setStartTime(newStartTime);
		appointment.setEndTime(newEndTime);
		appointment.setChangeReason(changeReason);

		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional
	public Appointment cancelByStaff(Integer appointmentId, String changeReason) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Only BOOKED appointments can be cancelled");
		}
		if (changeReason == null || changeReason.isBlank()) {
			throw new IllegalArgumentException("Change reason is required for staff cancellation");
		}

		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointment.setChangeReason(changeReason);

		this.slotOccupancies.deleteByOccupancyTypeAndReferenceId(OccupancyType.APPOINTMENT, appointment.getId());
		this.slotOccupancies.deleteByVetIdAndStartTime(appointment.getVet().getId(), appointment.getStartTime());

		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional
	public Appointment cancelByOwner(Integer appointmentId, Integer ownerId) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

		if (!appointment.getOwner().getId().equals(ownerId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized access to appointment");
		}
		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Only BOOKED appointments can be cancelled");
		}

		LocalDateTime now = this.availabilityService.getNow().toLocalDateTime();
		if (!appointment.getStartTime().isAfter(now)) {
			throw new IllegalStateException("Cannot cancel an appointment at or after its start time");
		}

		appointment.setStatus(AppointmentStatus.CANCELLED);
		appointment.setChangeReason("Cancelled by owner");

		this.slotOccupancies.deleteByOccupancyTypeAndReferenceId(OccupancyType.APPOINTMENT, appointment.getId());
		this.slotOccupancies.deleteByVetIdAndStartTime(appointment.getVet().getId(), appointment.getStartTime());

		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional
	public Appointment completeAppointment(Integer appointmentId) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Only BOOKED appointments can be marked COMPLETED");
		}

		appointment.setStatus(AppointmentStatus.COMPLETED);

		Owner owner = appointment.getOwner();
		Pet pet = appointment.getPet();
		Visit visit = new Visit();
		visit.setDate(appointment.getStartTime().toLocalDate());
		String desc = (appointment.getReason() != null && !appointment.getReason().isBlank()) ? appointment.getReason()
				: "Completed appointment";
		visit.setDescription(desc);
		owner.addVisit(pet.getId(), visit);
		this.owners.save(owner);

		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional
	public Appointment markNoShow(Integer appointmentId) {
		Appointment appointment = this.appointments.findById(appointmentId)
			.orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + appointmentId));

		if (appointment.getStatus() != AppointmentStatus.BOOKED) {
			throw new IllegalStateException("Only BOOKED appointments can be marked NO_SHOW");
		}

		appointment.setStatus(AppointmentStatus.NO_SHOW);

		return this.appointments.saveAndFlush(appointment);
	}

	@Transactional(readOnly = true)
	public Optional<Appointment> findAppointmentForRequest(Integer requestId) {
		return this.appointments.findBySchedulingRequestId(requestId);
	}

	@Transactional(readOnly = true)
	public Optional<Appointment> getAppointment(Integer appointmentId) {
		return this.appointments.findById(appointmentId);
	}

	@Transactional(readOnly = true)
	public List<Appointment> getUpcomingAppointmentsForOwner(Integer ownerId) {
		LocalDateTime now = this.availabilityService.getNow().toLocalDateTime();
		return this.appointments.findByOwnerIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(ownerId,
				AppointmentStatus.BOOKED, now);
	}

	@Transactional(readOnly = true)
	public List<Appointment> getAllAppointments() {
		return this.appointments.findAllByOrderByStartTimeAsc();
	}

	@Transactional(readOnly = true)
	public List<Appointment> getAppointmentsByStatus(AppointmentStatus status) {
		return this.appointments.findByStatusOrderByStartTimeAsc(status);
	}

	private void validateSlot(Integer vetId, LocalDateTime startTime, LocalDateTime endTime,
			Integer excludeOccupancyId) {
		if (startTime == null || endTime == null) {
			throw new IllegalArgumentException("Start time and end time must not be null");
		}
		if (!startTime.isBefore(endTime)) {
			throw new IllegalArgumentException("Start time must be before end time");
		}
		if (!startTime.toLocalDate().equals(endTime.toLocalDate())) {
			throw new IllegalArgumentException("Appointments must start and end on the same calendar day");
		}

		LocalDateTime now = this.availabilityService.getNow().toLocalDateTime();
		if (startTime.isBefore(now)) {
			throw new IllegalArgumentException("Cannot schedule an appointment in the past");
		}

		LocalDate maxDate = this.availabilityService.getHorizonEndDate();
		if (startTime.toLocalDate().isAfter(maxDate)) {
			throw new IllegalArgumentException("Requested date is beyond the maximum booking horizon");
		}

		List<VetAvailability> availabilities = this.availabilityService.getVetAvailability(vetId,
				startTime.toLocalDate());
		boolean isAvailable = availabilities.stream()
			.anyMatch(a -> !startTime.isBefore(a.startTime()) && !endTime.isAfter(a.endTime()));
		if (!isAvailable) {
			throw new IllegalStateException("Selected slot falls outside veterinarian bookable hours or on a closure");
		}

		List<SlotOccupancy> dayOccupancies = this.slotOccupancies.findByVetIdAndStartTimeBetween(vetId,
				startTime.toLocalDate().atStartOfDay(), startTime.toLocalDate().plusDays(1).atStartOfDay());
		boolean overlaps = dayOccupancies.stream()
			.filter(o -> excludeOccupancyId == null || !o.getId().equals(excludeOccupancyId))
			.anyMatch(o -> o.getStartTime().isBefore(endTime) && o.getEndTime().isAfter(startTime));
		if (overlaps) {
			throw new IllegalStateException("Selected slot conflicts with an existing appointment or active hold");
		}
	}

}
