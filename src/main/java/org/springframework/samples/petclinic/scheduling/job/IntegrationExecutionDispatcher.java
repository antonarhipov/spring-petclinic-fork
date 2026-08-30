package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IntegrationExecutionDispatcher {

	private final IntegrationExecutionRepository executions;

	private final IntegrationExecutionWorkerRegistry workers;

	private final ThreadPoolTaskExecutor executor;

	private final Clock clock;

	public IntegrationExecutionDispatcher(IntegrationExecutionRepository executions,
			IntegrationExecutionWorkerRegistry workers,
			@Qualifier("schedulingExecutor") ThreadPoolTaskExecutor executor, Clock clock) {
		this.executions = executions;
		this.workers = workers;
		this.executor = executor;
		this.clock = clock;
	}

	public void dispatchAfterCommit(UUID executionId) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					executor.execute(() -> run(executionId));
				}
			});
		}
		else {
			this.executor.execute(() -> run(executionId));
		}
	}

	public boolean claim(UUID executionId) {
		return this.executions.claimPending(executionId, Instant.now(this.clock)) == 1;
	}

	void run(UUID executionId) {
		if (!claim(executionId)) {
			return;
		}
		IntegrationExecution execution = this.executions.findById(executionId).orElseThrow();
		this.workers.find(execution.getKind()).ifPresent(worker -> worker.execute(executionId));
	}

}
