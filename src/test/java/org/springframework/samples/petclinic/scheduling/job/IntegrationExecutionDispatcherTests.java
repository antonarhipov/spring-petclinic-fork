package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecution;
import org.springframework.samples.petclinic.scheduling.audit.IntegrationExecutionRepository;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationExecutionDispatcherTests {

	@Test
	void duplicateClaimIsRejected() {
		IntegrationExecutionRepository repo = mock(IntegrationExecutionRepository.class);
		when(repo.claimPending(any(), any())).thenReturn(1, 0);
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.initialize();
		IntegrationExecutionDispatcher dispatcher = new IntegrationExecutionDispatcher(repo,
				new IntegrationExecutionWorkerRegistry(List.of()), executor, Clock.systemUTC());
		UUID id = UUID.randomUUID();
		assertThat(dispatcher.claim(id)).isTrue();
		assertThat(dispatcher.claim(id)).isFalse();
		executor.shutdown();
	}

	@Test
	void expiredRunningIsRecovered() {
		IntegrationExecutionRepository repo = mock(IntegrationExecutionRepository.class);
		IntegrationExecution running = new IntegrationExecution();
		running.setId(UUID.randomUUID());
		running.setState("RUNNING");
		running.setDeadlineAt(Instant.parse("2020-01-01T00:00:00Z"));
		when(repo.findByState("PENDING")).thenReturn(List.of());
		when(repo.findByState("RUNNING")).thenReturn(List.of(running));
		when(repo.save(running)).thenReturn(running);
		IntegrationExecutionDispatcher dispatcher = mock(IntegrationExecutionDispatcher.class);
		Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
		new IntegrationExecutionRecovery(repo, dispatcher, clock).recover();
		assertThat(running.getState()).isEqualTo("TIMED_OUT");
		assertThat(running.getOutcome()).isEqualTo("EXPIRED_RUNNING");
	}

	@Test
	void supersededCompletionDoesNotRerunWorker() {
		AtomicInteger runs = new AtomicInteger();
		IntegrationExecutionWorker worker = new IntegrationExecutionWorker() {
			@Override
			public String kind() {
				return "LLM_INTERPRETATION";
			}

			@Override
			public void execute(UUID executionId) {
				runs.incrementAndGet();
			}
		};
		IntegrationExecutionRepository repo = mock(IntegrationExecutionRepository.class);
		when(repo.claimPending(any(), any())).thenReturn(0);
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.initialize();
		IntegrationExecutionDispatcher dispatcher = new IntegrationExecutionDispatcher(repo,
				new IntegrationExecutionWorkerRegistry(List.of(worker)), executor, Clock.systemUTC());
		dispatcher.run(UUID.randomUUID());
		assertThat(runs.get()).isZero();
		executor.shutdown();
	}

}
