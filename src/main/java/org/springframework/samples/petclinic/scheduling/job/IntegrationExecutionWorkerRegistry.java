package org.springframework.samples.petclinic.scheduling.job;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class IntegrationExecutionWorkerRegistry {

	private final Map<String, IntegrationExecutionWorker> workers;

	public IntegrationExecutionWorkerRegistry(List<IntegrationExecutionWorker> workers) {
		this.workers = workers.stream()
			.collect(Collectors.toMap(IntegrationExecutionWorker::kind, Function.identity()));
	}

	public Optional<IntegrationExecutionWorker> find(String kind) {
		return Optional.ofNullable(this.workers.get(kind));
	}

}
