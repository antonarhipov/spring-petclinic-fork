package org.springframework.samples.petclinic.scheduling.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(SchedulingProperties.class)
public class AsyncExecutionConfiguration {

	@Bean(name = "schedulingExecutor")
	ThreadPoolTaskExecutor schedulingExecutor(SchedulingProperties properties) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("scheduling-job-");
		executor.setCorePoolSize(properties.getExecutor().getCorePoolSize());
		executor.setMaxPoolSize(properties.getExecutor().getMaxPoolSize());
		executor.setQueueCapacity(properties.getExecutor().getQueueCapacity());
		executor.initialize();
		return executor;
	}

}
