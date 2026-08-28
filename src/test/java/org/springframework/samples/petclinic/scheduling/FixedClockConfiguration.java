package org.springframework.samples.petclinic.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FixedClockConfiguration {

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(Instant.parse("2030-01-01T08:00:00Z"), ZoneOffset.UTC);
	}

}
