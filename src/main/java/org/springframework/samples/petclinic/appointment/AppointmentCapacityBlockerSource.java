package org.springframework.samples.petclinic.appointment;

import java.time.Instant;
import java.util.List;
import org.springframework.samples.petclinic.availability.CapacityBlockerSource;
import org.springframework.samples.petclinic.shared.TimeInterval;
import org.springframework.stereotype.Component;

@Component
public class AppointmentCapacityBlockerSource implements CapacityBlockerSource {

	private final AppointmentRepository appointmentRepository;

	public AppointmentCapacityBlockerSource(AppointmentRepository appointmentRepository) {
		this.appointmentRepository = appointmentRepository;
	}

	@Override
	public List<TimeInterval> findVetBlockers(Integer vetId, Instant startAt, Instant endAt) {
		return this.appointmentRepository.findOverlappingByVet(vetId, BookingState.CONFIRMED, startAt, endAt)
			.stream()
			.map(a -> TimeInterval.of(a.getStartAt(), a.getEndAt()))
			.toList();
	}

	@Override
	public List<TimeInterval> findPetBlockers(Integer petId, Instant startAt, Instant endAt) {
		return this.appointmentRepository.findOverlappingByPet(petId, BookingState.CONFIRMED, startAt, endAt)
			.stream()
			.map(a -> TimeInterval.of(a.getStartAt(), a.getEndAt()))
			.toList();
	}

	@Override
	public List<TimeInterval> findOwnerBlockers(Integer ownerId, Instant startAt, Instant endAt) {
		return this.appointmentRepository.findOverlappingByOwner(ownerId, BookingState.CONFIRMED, startAt, endAt)
			.stream()
			.map(a -> TimeInterval.of(a.getStartAt(), a.getEndAt()))
			.toList();
	}

}
