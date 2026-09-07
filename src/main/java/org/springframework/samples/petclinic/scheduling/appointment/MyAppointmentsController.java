package org.springframework.samples.petclinic.scheduling.appointment;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.AuthenticatedOwnerService;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyAppointmentsController {

	private final AuthenticatedOwnerService owners;

	private final AppointmentRepository appointments;

	private final SchedulingRequestRepository requests;

	public MyAppointmentsController(AuthenticatedOwnerService owners, AppointmentRepository appointments,
			SchedulingRequestRepository requests) {
		this.owners = owners;
		this.appointments = appointments;
		this.requests = requests;
	}

	@GetMapping("/my/appointments")
	String appointments(Principal principal, Model model) {
		Owner owner = this.owners.requireOwner(principal);
		Map<Integer, List<Appointment>> byPet = this.appointments
			.findByPetOwnerIdOrderByDateAscStartTimeAsc(owner.getId())
			.stream()
			.filter(appointment -> appointment.getStatus() != AppointmentStatus.HELD)
			.collect(Collectors.groupingBy(appointment -> appointment.getPet().getId()));
		Map<Integer, SchedulingRequest> activeByPet = this.requests
			.findByPetOwnerIdOrderByCreatedDateDescCreatedTimeDesc(owner.getId())
			.stream()
			.filter(request -> request.getActivePetId() != null)
			.collect(Collectors.toMap(request -> request.getPet().getId(), Function.identity(),
					(first, ignored) -> first));
		List<PetAppointments> pets = owner.getPets()
			.stream()
			.map(pet -> new PetAppointments(pet, byPet.getOrDefault(pet.getId(), List.of()),
					activeByPet.get(pet.getId())))
			.toList();
		model.addAttribute("pets", pets);
		return "my/appointments";
	}

	public record PetAppointments(Pet pet, List<Appointment> appointments, SchedulingRequest activeRequest) {

		public String statusMessageKey(Appointment appointment) {
			return switch (appointment.getStatus()) {
				case HELD -> "scheduling.appointment.status.held";
				case CONFIRMED -> "scheduling.appointment.status.confirmed";
				case CANCELLED -> "scheduling.appointment.status.cancelled";
				case COMPLETED -> "scheduling.appointment.status.completed";
				case NO_SHOW -> "scheduling.appointment.status.noShow";
			};
		}

		public String requestStateMessageKey() {
			return switch (this.activeRequest.getState()) {
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

	}

}
