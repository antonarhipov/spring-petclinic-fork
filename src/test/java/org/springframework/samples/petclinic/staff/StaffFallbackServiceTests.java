package org.springframework.samples.petclinic.staff;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.appointment.AppointmentRequest;
import org.springframework.samples.petclinic.appointment.AppointmentRequestRepository;
import org.springframework.samples.petclinic.appointment.AppointmentRequestStatus;
import org.springframework.samples.petclinic.appointment.AppointmentRequestWorkflowService;
import org.springframework.samples.petclinic.appointment.FallbackReason;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.Pet;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaffFallbackServiceTests {

	@Mock
	private AppointmentRequestRepository requestRepository;

	@Mock
	private AppointmentRequestWorkflowService workflowService;

	private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

	@Test
	void queueIncludesContextAndPrioritizesSuspectedEmergencies() {
		AppointmentRequest routine = request(1, "Routine check", FallbackReason.NO_FEASIBLE_SLOT,
				"{\"urgency\":\"ROUTINE\"}");
		AppointmentRequest emergency = request(2, "Cannot breathe", FallbackReason.AI_UNAVAILABLE,
				"{\"urgency\":\"SUSPECTED_EMERGENCY\"}");
		StaffFallbackService service = new StaffFallbackService(this.requestRepository, this.workflowService,
				this.jsonMapper);
		given(this.requestRepository.findByStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF))
			.willReturn(List.of(routine, emergency));

		List<StaffFallbackService.QueueItem> queue = service.getQueue();

		assertThat(queue).extracting(StaffFallbackService.QueueItem::requestId).containsExactly(2, 1);
		assertThat(queue.get(0).suspectedEmergency()).isTrue();
		assertThat(queue.get(0).ownerName()).isEqualTo("Ada Lovelace");
		assertThat(queue.get(0).petName()).isEqualTo("Byte");
		assertThat(queue.get(0).freeText()).isEqualTo("Cannot breathe");
		assertThat(queue.get(0).fallbackReason()).isEqualTo(FallbackReason.AI_UNAVAILABLE);
	}

	@Test
	void unblockDelegatesToGuardedWorkflow() {
		StaffFallbackService service = new StaffFallbackService(this.requestRepository, this.workflowService,
				this.jsonMapper);

		service.unblock(7);

		verify(this.workflowService).unblock(7);
	}

	private static AppointmentRequest request(int id, String text, FallbackReason reason, String interpretation) {
		Owner owner = new Owner();
		owner.setId(10);
		owner.setFirstName("Ada");
		owner.setLastName("Lovelace");
		Pet pet = new Pet();
		owner.addPet(pet);
		pet.setId(20);
		pet.setName("Byte");
		AppointmentRequest request = new AppointmentRequest();
		request.setId(id);
		request.setOwner(owner);
		request.setPet(pet);
		request.setFreeText(text);
		request.setFallbackReason(reason);
		request.setInterpretationJson(interpretation);
		request.setStatus(AppointmentRequestStatus.QUEUED_FOR_STAFF);
		return request;
	}

}
