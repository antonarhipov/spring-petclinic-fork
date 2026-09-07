package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock schedulingClock() {
		return Clock.system(ZoneId.of("Europe/Amsterdam"));
	}

}
