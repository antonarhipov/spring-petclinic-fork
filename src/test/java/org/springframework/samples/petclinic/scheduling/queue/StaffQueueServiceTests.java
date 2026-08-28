package org.springframework.samples.petclinic.scheduling.queue;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.owner.Pet;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

class StaffQueueServiceTests {

	@Test
	void emergencyRequestsGetEmergencyPriority() {
		StaffQueueItem item = new StaffQueueItem(new SchedulingRequest(new Pet(), true), QueuePriority.EMERGENCY);
		assertThat(item.getPriority()).isEqualTo(QueuePriority.EMERGENCY);
		assertThat(item.getState()).isEqualTo(QueueState.NEW);
	}

}
