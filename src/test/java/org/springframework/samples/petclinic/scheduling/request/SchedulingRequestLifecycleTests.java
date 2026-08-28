package org.springframework.samples.petclinic.scheduling.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Pet;

class SchedulingRequestLifecycleTests {

	@Test
	void requestTracksExplicitLifecycleAndImmutablePet() {
		Pet pet = new Pet();
		SchedulingRequest request = new SchedulingRequest(pet, false);
		RequestRevision revision = new RequestRevision(request, 1, "A detailed scheduling request", true, Instant.now(),
				"id");
		request.setCurrentRevision(revision);
		revision.confirm(Instant.now());
		request.moveTo(SchedulingRequestState.READY_FOR_SUGGESTION);
		request.moveTo(SchedulingRequestState.OFFER_HELD);
		request.moveTo(SchedulingRequestState.CLOSED);
		assertThat(request.getPet()).isSameAs(pet);
		assertThat(request.getState()).isEqualTo(SchedulingRequestState.CLOSED);
		assertThat(revision.getStatus()).isEqualTo(RevisionStatus.CONFIRMED);
	}

}
