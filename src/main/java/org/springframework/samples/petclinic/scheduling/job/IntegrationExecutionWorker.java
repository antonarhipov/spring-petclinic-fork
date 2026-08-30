package org.springframework.samples.petclinic.scheduling.job;

import java.util.UUID;

public interface IntegrationExecutionWorker {

	String kind();

	void execute(UUID executionId);

}
