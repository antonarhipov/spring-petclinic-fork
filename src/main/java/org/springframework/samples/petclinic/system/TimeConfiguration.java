package org.springframework.samples.petclinic.system;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.samples.petclinic.scheduling.config.ClinicProperties;

@Configuration
public class TimeConfiguration {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	public ZoneId clinicZoneId(ClinicProperties clinicProperties) {
		return ZoneId.of(clinicProperties.getClinicZoneId());
	}

}
