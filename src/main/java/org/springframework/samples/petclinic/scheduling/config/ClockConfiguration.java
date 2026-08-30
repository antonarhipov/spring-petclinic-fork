package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ClockConfiguration {

	@Bean
	Clock utcClock() {
		return Clock.systemUTC();
	}

}
