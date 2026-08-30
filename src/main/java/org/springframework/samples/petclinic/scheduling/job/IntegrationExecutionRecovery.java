package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IntegrationExecutionRecovery {

	private final IntegrationExecutionRepository executions;

	private final IntegrationExecutionDispatcher dispatcher;

	private final Clock clock;

	public IntegrationExecutionRecovery(IntegrationExecutionRepository executions,
			IntegrationExecutionDispatcher dispatcher, Clock clock) {
		this.executions = executions;
		this.dispatcher = dispatcher;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void recover() {
		Instant now = Instant.now(this.clock);
		for (IntegrationExecution pending : this.executions.findByState("PENDING")) {
			this.dispatcher.dispatchAfterCommit(pending.getId());
		}
		for (IntegrationExecution running : this.executions.findByState("RUNNING")) {
			if (running.getDeadlineAt() != null && running.getDeadlineAt().isBefore(now)) {
				running.setState("TIMED_OUT");
				running.setFinishedAt(now);
				running.setOutcome("EXPIRED_RUNNING");
				this.executions.save(running);
			}
		}
	}

}
