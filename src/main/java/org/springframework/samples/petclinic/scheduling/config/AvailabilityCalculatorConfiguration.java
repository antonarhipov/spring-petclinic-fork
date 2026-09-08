package org.springframework.samples.petclinic.scheduling.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailabilityCalculator;

@Configuration(proxyBeanMethods = false)
class AvailabilityCalculatorConfiguration {

	@Bean
	EffectiveAvailabilityCalculator effectiveAvailabilityCalculator() {
		return new EffectiveAvailabilityCalculator();
	}

}
