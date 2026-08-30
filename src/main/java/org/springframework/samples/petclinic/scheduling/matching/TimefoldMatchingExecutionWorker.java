package org.springframework.samples.petclinic.scheduling.matching;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.samples.petclinic.scheduling.job.IntegrationExecutionWorker;
import org.springframework.stereotype.Component;

@Component
public class TimefoldMatchingExecutionWorker implements IntegrationExecutionWorker {

	private final MatchingCoordinator coordinator;

	public TimefoldMatchingExecutionWorker(@Lazy MatchingCoordinator coordinator) {
		this.coordinator = coordinator;
	}

	@Override
	public String kind() {
		return "TIMEFOLD_MATCH";
	}

	@Override
	public void execute(UUID executionId) {
		this.coordinator.execute(executionId);
	}

}
