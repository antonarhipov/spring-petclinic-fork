package org.springframework.samples.petclinic.scheduling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.queue.OwnerStaffHandlingProjection;
import org.springframework.samples.petclinic.scheduling.queue.QueueNote;

import static org.assertj.core.api.Assertions.assertThat;

class StaffQueueAuditVisibilityTests {

	@Test
	void ownerProjectionOmitsNotesScoresAndErrorClasses() {
		OwnerStaffHandlingProjection projection = OwnerStaffHandlingProjection.of();
		QueueNote note = new QueueNote();
		note.setBody("do not show to owner");
		assertThat(projection.status()).isEqualTo("STAFF_HANDLING");
		assertThat(projection.toString()).doesNotContain(note.getBody())
			.doesNotContain("score")
			.doesNotContain("errorClassification");
	}

}
