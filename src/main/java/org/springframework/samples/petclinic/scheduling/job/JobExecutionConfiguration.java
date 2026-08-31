package org.springframework.samples.petclinic.scheduling.job;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(name = "petclinic.jobs.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class JobExecutionConfiguration {

}
