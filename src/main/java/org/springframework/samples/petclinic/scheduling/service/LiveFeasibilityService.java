package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;

@Service
public class LiveFeasibilityService {

	private final VetRepository vetRepository;

	private final AppointmentRepository appointmentRepository;

	private final ReservationRepository reservationRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final RequestAvailabilityIntervalRepository availabilityIntervalRepository;

	private final SchedulingTimeService timeService;

	public LiveFeasibilityService(VetRepository vetRepository, AppointmentRepository appointmentRepository,
			ReservationRepository reservationRepository, EffectiveAvailabilityService effectiveAvailabilityService,
			RequestAvailabilityIntervalRepository availabilityIntervalRepository, SchedulingTimeService timeService) {
		this.vetRepository = vetRepository;
		this.appointmentRepository = appointmentRepository;
		this.reservationRepository = reservationRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.availabilityIntervalRepository = availabilityIntervalRepository;
		this.timeService = timeService;
	}

	public boolean checkFeasibility(Integer vetId, Integer ownerId, Integer petId, Instant startTime, Instant endTime,
			CareType careType, Specialty requiredSpecialty, Instant horizonEnd, Integer excludeRequestId) {
		return checkFeasibility(vetId, ownerId, petId, startTime, endTime, careType, requiredSpecialty, horizonEnd,
				excludeRequestId, null);
	}

	public boolean checkFeasibility(Integer vetId, Integer ownerId, Integer petId, Instant startTime, Instant endTime,
			CareType careType, Specialty requiredSpecialty, Instant horizonEnd, Integer excludeRequestId,
			Integer excludeAppointmentId) {

		// 1. Check DST validity
		if (!timeService.isValidDstOffset(startTime) || !timeService.isValidDstOffset(endTime)) {
			return false;
		}

		// 2. Check 15-minute grid alignment
		if (startTime.getEpochSecond() % 900 != 0 || endTime.getEpochSecond() % 900 != 0) {
			return false;
		}

		// 3. Check horizon bounds
		Instant nextGrid = timeService.quantizeToNext15MinuteBoundary(timeService.now());
		if (startTime.isBefore(nextGrid)) {
			return false;
		}
		if (horizonEnd != null && !startTime.isBefore(horizonEnd)) {
			return false;
		}
		if (excludeRequestId != null) {
			OwnerWindowPolicy.Evaluation ownerWindow = OwnerWindowPolicy.evaluate(
					availabilityIntervalRepository.findByRequestIdOrderByStartInstantAscIdAsc(excludeRequestId),
					startTime, endTime);
			if (!ownerWindow.allowed() || ownerWindow.excluded()) {
				return false;
			}
		}

		// 4. Check vet active & specialty eligibility
		Vet vet = vetRepository.findById(vetId).orElse(null);
		if (vet == null || !vet.isActive()) {
			return false;
		}
		if (careType == CareType.SPECIALTY) {
			if (requiredSpecialty == null) {
				return false;
			}
			boolean hasSpecialty = false;
			for (Specialty s : vet.getSpecialties()) {
				if (s.isActive() && Objects.equals(s.getId(), requiredSpecialty.getId())) {
					hasSpecialty = true;
					break;
				}
			}
			if (!hasSpecialty) {
				return false;
			}
		}

		// 5. Contiguous effective availability check
		List<TimeInterval> availability = effectiveAvailabilityService.calculateEffectiveAvailability(vetId, startTime,
				endTime);
		boolean inContiguous = false;
		for (TimeInterval interval : availability) {
			if (!startTime.isBefore(interval.getStart()) && !endTime.isAfter(interval.getEnd())) {
				inContiguous = true;
				break;
			}
		}
		if (!inContiguous) {
			return false;
		}

		// 6. Check appointment overlap for vet, owner, and pet
		List<Appointment> vetAppts = appointmentRepository
			.findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(vetId, AppointmentStatus.BOOKED, endTime,
					startTime);
		for (Appointment a : vetAppts) {
			if (excludeAppointmentId == null || !Objects.equals(a.getId(), excludeAppointmentId)) {
				return false;
			}
		}

		if (ownerId != null) {
			List<Appointment> ownerAppts = appointmentRepository
				.findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(ownerId, AppointmentStatus.BOOKED,
						endTime, startTime);
			for (Appointment a : ownerAppts) {
				if (excludeAppointmentId == null || !Objects.equals(a.getId(), excludeAppointmentId)) {
					return false;
				}
			}
		}

		if (petId != null) {
			List<Appointment> petAppts = appointmentRepository
				.findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(petId, AppointmentStatus.BOOKED, endTime,
						startTime);
			for (Appointment a : petAppts) {
				if (excludeAppointmentId == null || !Objects.equals(a.getId(), excludeAppointmentId)) {
					return false;
				}
			}
		}

		// 7. Check active reservation overlap for vet, owner, and pet
		List<Reservation> vetRes = reservationRepository.findByVetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(
				vetId, ReservationStatus.ACTIVE, endTime, startTime);
		for (Reservation r : vetRes) {
			if (excludeRequestId == null || !Objects.equals(r.getRequest().getId(), excludeRequestId)) {
				return false;
			}
		}

		if (ownerId != null) {
			List<Reservation> ownerRes = reservationRepository
				.findByOwnerIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(ownerId, ReservationStatus.ACTIVE,
						endTime, startTime);
			for (Reservation r : ownerRes) {
				if (excludeRequestId == null || !Objects.equals(r.getRequest().getId(), excludeRequestId)) {
					return false;
				}
			}
		}

		if (petId != null) {
			List<Reservation> petRes = reservationRepository
				.findByPetIdAndStatusAndStartTimeLessThanAndEndTimeGreaterThan(petId, ReservationStatus.ACTIVE, endTime,
						startTime);
			for (Reservation r : petRes) {
				if (excludeRequestId == null || !Objects.equals(r.getRequest().getId(), excludeRequestId)) {
					return false;
				}
			}
		}

		return true;
	}

}
