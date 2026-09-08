package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	@Bean
	@ConditionalOnMissingBean(Clock.class)
	Clock schedulingClock(JdbcTemplate jdbc) {
		return new ClinicZoneClock(Clock.systemUTC(), jdbc);
	}

}
