package org.springframework.samples.petclinic.scheduling.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerStaffHandlingProjectionTests {

	@Test
	void ownerProjectionOmitsStaffInternals() {
		OwnerStaffHandlingProjection projection = OwnerStaffHandlingProjection.of();
		assertThat(projection.status()).isEqualTo("STAFF_HANDLING");
		assertThat(projection.toString()).doesNotContain("assignee")
			.doesNotContain("score")
			.doesNotContain("errorClassification");
	}

}
