package org.springframework.samples.petclinic.scheduling.request;

import java.time.Instant;
import java.util.List;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.appointment.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.appointment.Offer;
import org.springframework.samples.petclinic.scheduling.appointment.OfferRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerSchedulingQueryService {

	private final OwnerRepository owners;

	private final AppointmentRepository appointments;

	private final SchedulingRequestRepository requests;

	private final OfferRepository offers;

	private final VetRepository vets;

	public OwnerSchedulingQueryService(OwnerRepository owners, AppointmentRepository appointments,
			SchedulingRequestRepository requests, OfferRepository offers, VetRepository vets) {
		this.owners = owners;
		this.appointments = appointments;
		this.requests = requests;
		this.offers = offers;
		this.vets = vets;
	}

	@Transactional(readOnly = true)
	public Owner profile(Integer ownerId) {
		return this.owners.findById(ownerId).orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public List<OwnerAppointmentView> appointments(Integer ownerId) {
		Owner owner = profile(ownerId);
		List<Integer> petIds = owner.getPets().stream().map(Pet::getId).toList();
		if (petIds.isEmpty()) {
			return List.of();
		}
		return this.appointments.findByPetIdInOrderByStartAtDesc(petIds).stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	public OwnerAppointmentView appointment(Integer ownerId, Long appointmentId) {
		return appointments(ownerId).stream()
			.filter(view -> view.id().equals(appointmentId))
			.findFirst()
			.orElseThrow(OwnerResourceNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public OwnerHistoryView history(Integer ownerId) {
		Owner owner = profile(ownerId);
		List<OwnerAppointmentView> appointmentViews = appointments(ownerId);
		List<OwnerVisitView> visits = owner.getPets()
			.stream()
			.flatMap(pet -> pet.getVisits()
				.stream()
				.map(visit -> new OwnerVisitView(visit.getId(), pet.getName(),
						visit.getDate() == null ? null : visit.getDate().toString(), visit.getDescription())))
			.toList();
		List<OwnerRequestView> requestViews = this.requests.findByOwnerIdOrderByUpdatedAtDesc(ownerId)
			.stream()
			.map(request -> new OwnerRequestView(request.getId(), request.getState().name(),
					request.getOwnerStatusCode(), request.getUpdatedAt()))
			.toList();
		return new OwnerHistoryView(appointmentViews, visits, requestViews);
	}

	@Transactional(readOnly = true)
	public Offer requireOwnedOffer(Integer ownerId, Long offerId) {
		Offer offer = this.offers.findById(offerId).orElseThrow(OwnerResourceNotFoundException::new);
		boolean owned = this.requests.findByOwnerIdOrderByUpdatedAtDesc(ownerId)
			.stream()
			.anyMatch(request -> offer.getRequestRevisionId().equals(request.getActiveRequestRevisionId()));
		if (!owned) {
			throw new OwnerResourceNotFoundException();
		}
		return offer;
	}

	private OwnerAppointmentView view(Appointment appointment) {
		String vetName = this.vets.findById(appointment.getVeterinarianId())
			.map(vet -> vet.getFirstName() + " " + vet.getLastName())
			.orElse("");
		String specialty = this.vets.findById(appointment.getVeterinarianId())
			.map(vet -> vet.getSpecialties()
				.stream()
				.map(org.springframework.samples.petclinic.vet.Specialty::getName)
				.reduce((a, b) -> a + ", " + b)
				.orElse(""))
			.orElse("");
		return new OwnerAppointmentView(appointment.getId(), appointment.getStatus().name(), appointment.getStartAt(),
				appointment.getEndAt(), vetName, specialty, appointment.getPetId());
	}

	public record OwnerAppointmentView(Long id, String status, Instant startAt, Instant endAt, String veterinarianName,
			String veterinarianSpecialty, Integer petId) {
	}

	public record OwnerVisitView(Integer id, String petName, String date, String description) {
	}

	public record OwnerRequestView(Long id, String state, String ownerStatusCode, Instant updatedAt) {
	}

	public record OwnerHistoryView(List<OwnerAppointmentView> appointments, List<OwnerVisitView> visits,
			List<OwnerRequestView> requests) {
	}

}
