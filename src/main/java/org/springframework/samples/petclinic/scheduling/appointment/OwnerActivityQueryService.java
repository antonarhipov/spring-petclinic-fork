package org.springframework.samples.petclinic.scheduling.appointment;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.AuthenticatedOwnerService;
import org.springframework.samples.petclinic.scheduling.request.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.InterpretationOrigin;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OwnerActivityQueryService {

	private final AuthenticatedOwnerService owners;

	private final AppointmentRepository appointments;

	private final SchedulingRequestRepository requests;

	private final Clock clock;

	public OwnerActivityQueryService(AuthenticatedOwnerService owners, AppointmentRepository appointments,
			SchedulingRequestRepository requests, Clock clock) {
		this.owners = owners;
		this.appointments = appointments;
		this.requests = requests;
		this.clock = clock;
	}

	public OwnerPetsView petsFor(Principal principal) {
		Owner owner = this.owners.requireOwner(principal);
		return new OwnerPetsView(owner.getFirstName(), owner.getLastName(), owner.getAddress(), owner.getCity(),
				owner.getTelephone(), owner.getPets().stream().map(this::petView).toList());
	}

	public List<PetActivityView> activityFor(Principal principal) {
		Owner owner = this.owners.requireOwner(principal);
		List<Appointment> allAppointments = this.appointments.findByPetOwnerIdOrderByDateAscStartTimeAsc(owner.getId());
		Map<Integer, List<Appointment>> appointmentsByPet = allAppointments.stream()
			.filter(appointment -> appointment.getStatus() != AppointmentStatus.HELD)
			.collect(Collectors.groupingBy(appointment -> appointment.getPet().getId()));
		Map<Integer, Appointment> holdsByRequest = allAppointments.stream()
			.filter(appointment -> appointment.getStatus() == AppointmentStatus.HELD)
			.filter(appointment -> appointment.getRequest() != null)
			.collect(Collectors.toMap(appointment -> appointment.getRequest().getId(), Function.identity()));
		Map<Integer, SchedulingRequest> activeRequestsByPet = this.requests
			.findByPetOwnerIdOrderByCreatedDateDescCreatedTimeDesc(owner.getId())
			.stream()
			.filter(request -> request.getActivePetId() != null)
			.collect(Collectors.toMap(request -> request.getPet().getId(), Function.identity(),
					(first, ignored) -> first));
		LocalDate today = LocalDate.now(this.clock);
		LocalTime now = LocalTime.now(this.clock);
		return owner.getPets()
			.stream()
			.map(pet -> new PetActivityView(petView(pet),
					appointmentsByPet.getOrDefault(pet.getId(), List.of())
						.stream()
						.map(appointment -> appointmentView(appointment, today, now))
						.toList(),
					requestView(activeRequestsByPet.get(pet.getId()), holdsByRequest)))
			.toList();
	}

	private PetView petView(Pet pet) {
		return new PetView(pet.getId(), pet.getName(), pet.getBirthDate(), pet.getType().getName());
	}

	private AppointmentView appointmentView(Appointment appointment, LocalDate today, LocalTime now) {
		Vet vet = appointment.getVet();
		return new AppointmentView(appointment.getId(), appointment.getDate(), appointment.getStartTime(),
				appointment.getEndTime(), veterinarianName(vet), specialties(vet),
				statusMessageKey(appointment.getStatus()), appointment.getLastChangeReason(),
				appointment.isCancellableByOwnerAt(today, now));
	}

	private RequestView requestView(SchedulingRequest request, Map<Integer, Appointment> holdsByRequest) {
		if (request == null) {
			return null;
		}
		Interpretation interpretation = request.getCurrentInterpretation();
		Appointment hold = holdsByRequest.get(request.getId());
		Vet veterinarian = hold != null ? hold.getVet()
				: interpretation == null ? null : interpretation.getPreferredVet();
		return new RequestView(request.getId(), requestStateMessageKey(request.getState()),
				interpretation == null ? null : originMessageKey(interpretation.getOrigin()),
				interpretation == null ? null : interpretation.getSpecialty(),
				veterinarian == null ? null : veterinarianName(veterinarian),
				veterinarian == null ? List.of() : specialties(veterinarian));
	}

	private String veterinarianName(Vet veterinarian) {
		return veterinarian.getFirstName() + " " + veterinarian.getLastName();
	}

	private List<String> specialties(Vet veterinarian) {
		return veterinarian.getSpecialties().stream().map(specialty -> specialty.getName()).toList();
	}

	private String statusMessageKey(AppointmentStatus status) {
		return switch (status) {
			case HELD -> "scheduling.appointment.status.held";
			case CONFIRMED -> "scheduling.appointment.status.confirmed";
			case CANCELLED -> "scheduling.appointment.status.cancelled";
			case COMPLETED -> "scheduling.appointment.status.completed";
			case NO_SHOW -> "scheduling.appointment.status.noShow";
		};
	}

	private String requestStateMessageKey(RequestState state) {
		return switch (state) {
			case AWAITING_CONSENT -> "scheduling.request.state.awaitingConsent";
			case INTERPRETING -> "scheduling.request.state.interpreting";
			case INTERPRETATION_FAILED -> "scheduling.request.state.interpretationFailed";
			case INTERPRETED -> "scheduling.request.state.interpreted";
			case SUGGESTION_OFFERED -> "scheduling.request.state.suggestionOffered";
			case WITH_STAFF -> "scheduling.request.state.withStaff";
			case ACCEPTED -> "scheduling.request.state.accepted";
			case ABANDONED -> "scheduling.request.state.abandoned";
		};
	}

	private String originMessageKey(InterpretationOrigin origin) {
		return switch (origin) {
			case AI -> "scheduling.interpretation.origin.ai";
			case STAFF -> "scheduling.interpretation.origin.staff";
		};
	}

	public record OwnerPetsView(String firstName, String lastName, String address, String city, String telephone,
			List<PetView> pets) {
	}

	public record PetView(Integer id, String name, LocalDate birthDate, String type) {
	}

	public record PetActivityView(PetView pet, List<AppointmentView> appointments, RequestView activeRequest) {
	}

	public record AppointmentView(Integer id, LocalDate date, LocalTime startTime, LocalTime endTime,
			String veterinarianName, List<String> specialties, String statusMessageKey, String staffReason,
			boolean cancelAvailable) {
	}

	public record RequestView(Integer id, String stateMessageKey, String interpretationOriginMessageKey,
			String interpretedSpecialty, String veterinarianName, List<String> veterinarianSpecialties) {
	}

}
