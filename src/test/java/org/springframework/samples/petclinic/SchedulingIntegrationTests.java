package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettingsService;
import org.springframework.samples.petclinic.scheduling.request.UrgentCareGuidanceService;

@SpringBootTest
class SchedulingIntegrationTests {

	@Autowired
	private ClinicSchedulingSettingsService settings;

	@Autowired
	private UrgentCareGuidanceService guidance;

	@Test
	void defaultSchedulingSettingsExposeSafeUrgentCareGuidance() {
		assertThat(this.settings.get().getClinicZone()).isEqualTo("Europe/Amsterdam");
		assertThat(this.settings.get().getOfferHoldMinutes()).isEqualTo(10);
		assertThat(this.guidance.guidance()).isNotBlank();
	}

}
