package org.springframework.samples.petclinic.scheduling.availability;

import java.time.DayOfWeek;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ClinicPolicyRepositoryTests {

	@Autowired
	AvailabilityRepository policies;

	@Autowired
	AllowedDurationRepository durations;

	@Autowired
	ClinicHoursRepository hours;

	@Test
	void seedPolicyHasDefaultsAndVersion() {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		assertThat(policy.getZoneId()).isEqualTo("Europe/Amsterdam");
		assertThat(policy.getGridMinutes()).isEqualTo(15);
		assertThat(policy.getOwnerMinimumNoticeMinutes()).isEqualTo(120);
		assertThat(policy.getBookingHorizonDays()).isEqualTo(90);
		assertThat(policy.getHoldDurationMinutes()).isEqualTo(10);
		assertThat(policy.getConfigurationVersion()).isGreaterThanOrEqualTo(1);
		assertThat(policy.getVersion()).isNotNull();
		List<AllowedDuration> allowed = this.durations.findByPolicyId(policy.getId());
		assertThat(allowed).extracting(AllowedDuration::getDurationMinutes).containsExactlyInAnyOrder(15, 30, 45, 60);
		assertThat(this.hours.findByPolicyId(policy.getId())).extracting(ClinicHours::getDayOfWeek)
			.contains(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
	}

}
