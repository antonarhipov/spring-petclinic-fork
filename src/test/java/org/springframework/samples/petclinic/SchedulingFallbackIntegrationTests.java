package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.request.EmergencyScreeningService;

class SchedulingFallbackIntegrationTests {

	@Test
	void emergencyTermsUsePriorityWithoutAssuringNonUrgency() {
		EmergencyScreeningService service = new EmergencyScreeningService();
		assertThat(service.indicatesEmergency("My pet is having a seizure")).isTrue();
		assertThat(service.indicatesEmergency("Routine check-up")).isFalse();
	}

}
