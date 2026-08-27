package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.samples.petclinic.scheduling.dto.TimeInterval;
import org.springframework.samples.petclinic.scheduling.model.Appointment;
import org.springframework.samples.petclinic.scheduling.model.AppointmentStatus;
import org.springframework.samples.petclinic.scheduling.model.CareType;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.model.Rejection;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;
import org.springframework.samples.petclinic.scheduling.model.Reservation;
import org.springframework.samples.petclinic.scheduling.model.ReservationStatus;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.RejectionRepository;
import org.springframework.samples.petclinic.scheduling.repository.RequestAvailabilityIntervalRepository;
import org.springframework.samples.petclinic.scheduling.repository.ReservationRepository;
import org.springframework.samples.petclinic.scheduling.service.EffectiveAvailabilityService;
import org.springframework.samples.petclinic.scheduling.service.OwnerWindowPolicy;
import org.springframework.samples.petclinic.scheduling.service.SchedulingTimeService;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;

@Component
public class SchedulingProblemFactory {

	private final VetRepository vetRepository;

	private final AppointmentRepository appointmentRepository;

	private final ReservationRepository reservationRepository;

	private final RejectionRepository rejectionRepository;

	private final RequestAvailabilityIntervalRepository availabilityIntervalRepository;

	private final EffectiveAvailabilityService effectiveAvailabilityService;

	private final SchedulingTimeService timeService;

	public SchedulingProblemFactory(VetRepository vetRepository, AppointmentRepository appointmentRepository,
			ReservationRepository reservationRepository, RejectionRepository rejectionRepository,
			RequestAvailabilityIntervalRepository availabilityIntervalRepository,
			EffectiveAvailabilityService effectiveAvailabilityService, SchedulingTimeService timeService) {
		this.vetRepository = vetRepository;
		this.appointmentRepository = appointmentRepository;
		this.reservationRepository = reservationRepository;
		this.rejectionRepository = rejectionRepository;
		this.availabilityIntervalRepository = availabilityIntervalRepository;
		this.effectiveAvailabilityService = effectiveAvailabilityService;
		this.timeService = timeService;
	}

	public SchedulingProblem createProblem(SchedulingRequest request, ClinicSettings settings, Instant now) {
		Instant horizonStart = timeService.quantizeToNext15MinuteBoundary(now);
		Instant horizonEnd = request.getOwnerHorizonEnd();
		if (horizonEnd == null) {
			horizonEnd = timeService.calculateOwnerHorizonEnd(horizonStart, settings.getOwnerBookingHorizonDays());
		}

		Integer durationMinutes = request.getDurationMinutes();
		if (durationMinutes == null || durationMinutes <= 0) {
			durationMinutes = settings.getDefaultDurationMinutes();
		}
		Duration duration = Duration.ofMinutes(durationMinutes);

		List<Vet> allVets = (List<Vet>) vetRepository.findAll();
		List<Vet> eligibleVets = new ArrayList<>();
		for (Vet v : allVets) {
			if (!v.isActive()) {
				continue;
			}
			if (request.getCareType() == CareType.SPECIALTY) {
				if (request.getRequiredSpecialty() == null) {
					continue;
				}
				boolean hasSpecialty = false;
				for (Specialty s : v.getSpecialties()) {
					if (s.isActive() && Objects.equals(s.getId(), request.getRequiredSpecialty().getId())) {
						hasSpecialty = true;
						break;
					}
				}
				if (!hasSpecialty) {
					continue;
				}
			}
			eligibleVets.add(v);
		}

		// Rejections for this request
		List<Rejection> rejections = rejectionRepository.findByRequestId(request.getId());
		Set<String> rejectionKeys = new HashSet<>();
		for (Rejection r : rejections) {
			rejectionKeys.add(r.getVet().getId() + "#" + r.getStartTime().toString());
		}

		// Appointments in horizon
		List<Appointment> allAppointments = appointmentRepository.findAll();
		List<AppointmentFact> appointmentFacts = new ArrayList<>();
		Map<Integer, Long> vetWorkloadMinutes = new HashMap<>();

		Instant workloadWindowStart = request.getPreferredStartWindow() != null ? request.getPreferredStartWindow()
				: horizonStart;
		Instant workloadWindowEnd = horizonEnd;

		for (Appointment appt : allAppointments) {
			if (appt.getStatus() != AppointmentStatus.CANCELLED) {
				appointmentFacts.add(new AppointmentFact(appt.getId(), appt.getVet().getId(), appt.getOwner().getId(),
						appt.getPet().getId(), appt.getStartTime(), appt.getEndTime(), appt.getStatus()));

				// Compute workload for candidate vets in the window
				Integer vId = appt.getVet().getId();
				Instant apptStart = appt.getStartTime();
				Instant apptEnd = appt.getEndTime();
				Instant overlapStart = apptStart.isAfter(workloadWindowStart) ? apptStart : workloadWindowStart;
				Instant overlapEnd = apptEnd.isBefore(workloadWindowEnd) ? apptEnd : workloadWindowEnd;
				if (overlapStart.isBefore(overlapEnd)) {
					long mins = Duration.between(overlapStart, overlapEnd).toMinutes();
					vetWorkloadMinutes.put(vId, vetWorkloadMinutes.getOrDefault(vId, 0L) + mins);
				}
			}
		}

		// Active reservations in horizon
		List<Reservation> allReservations = reservationRepository.findAll();
		List<ReservationFact> reservationFacts = new ArrayList<>();
		for (Reservation res : allReservations) {
			if (res.getStatus() == ReservationStatus.ACTIVE) {
				reservationFacts.add(new ReservationFact(res.getId(), res.getVet().getId(), res.getOwner().getId(),
						res.getPet().getId(), res.getStartTime(), res.getEndTime(), res.getStatus()));
			}
		}

		// Candidate slots generator
		List<CandidateSlot> candidates = new ArrayList<>();
		Integer ownerId = request.getOwner().getId();
		Integer petId = request.getPet().getId();
		Integer preferredVetId = request.getPreferredVet() != null ? request.getPreferredVet().getId() : null;
		List<RequestAvailabilityInterval> ownerWindows = availabilityIntervalRepository
			.findByRequestIdOrderByStartInstantAscIdAsc(request.getId());

		for (Vet vet : eligibleVets) {
			List<TimeInterval> availability = effectiveAvailabilityService.calculateEffectiveAvailability(vet.getId(),
					horizonStart, horizonEnd);
			long workload = vetWorkloadMinutes.getOrDefault(vet.getId(), 0L);

			Instant current = horizonStart;
			while (current.isBefore(horizonEnd)) {
				Instant slotStart = current;
				Instant slotEnd = slotStart.plus(duration);

				boolean validDst = timeService.isValidDstOffset(slotStart) && timeService.isValidDstOffset(slotEnd);
				boolean startOnOrAfterGrid = !slotStart.isBefore(horizonStart);
				boolean startBeforeHorizon = slotStart.isBefore(horizonEnd);

				// Contiguous availability check
				boolean inContiguous = false;
				for (TimeInterval interval : availability) {
					if (!slotStart.isBefore(interval.getStart()) && !slotEnd.isAfter(interval.getEnd())) {
						inContiguous = true;
						break;
					}
				}

				boolean exactRejected = rejectionKeys.contains(vet.getId() + "#" + slotStart.toString());

				OwnerWindowPolicy.Evaluation windowEvaluation = OwnerWindowPolicy.evaluate(ownerWindows, slotStart,
						slotEnd);

				boolean preferredVet = (preferredVetId != null && Objects.equals(vet.getId(), preferredVetId));

				CandidateSlot slot = new CandidateSlot(vet.getId(), ownerId, petId, slotStart, slotEnd, validDst,
						startOnOrAfterGrid, startBeforeHorizon, inContiguous, false, true, windowEvaluation.allowed(),
						windowEvaluation.excluded(), exactRejected, windowEvaluation.preferred(), preferredVet,
						workload);

				candidates.add(slot);
				current = current.plus(Duration.ofMinutes(15));
			}
		}

		SchedulingRequestFact requestFact = new SchedulingRequestFact(request.getId(), ownerId, petId,
				request.getCareType(),
				request.getRequiredSpecialty() != null ? request.getRequiredSpecialty().getId() : null, preferredVetId,
				durationMinutes, horizonStart, horizonEnd, request.getPreferredStartWindow(),
				request.getPreferredEndWindow());

		ProposedAssignment assignment = new ProposedAssignment("assignment-1");

		return new SchedulingProblem(assignment, candidates, requestFact, appointmentFacts, reservationFacts);
	}

}
