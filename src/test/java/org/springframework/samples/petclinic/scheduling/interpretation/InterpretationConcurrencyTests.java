package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.support.DeterministicInterpreter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "scheduling.interpretation.deadline=5s")
@ActiveProfiles("test")
class InterpretationConcurrencyTests {

	@Autowired
	private RequestService requests;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DeterministicInterpreter interpreter;

	@Autowired
	@Qualifier("interpretationExecutor")
	private Executor interpretationExecutor;

	@BeforeEach
	void setUp() {
		this.interpreter.reset();
		this.jdbc.update("update scheduling_requests set current_interpretation_id = null");
		this.jdbc.update("delete from interpretation_windows");
		this.jdbc.update("delete from interpretation_failures");
		this.jdbc.update("delete from request_rejections");
		this.jdbc.update("delete from appointments");
		this.jdbc.update("delete from interpretations");
		this.jdbc.update("delete from scheduling_requests");
	}

	@AfterEach
	void releaseInterpreter() {
		this.interpreter.reset();
	}

	@Test
	void uc3G19_runsTwoInterpretationsAndQueuesEveryAdditionalJob() throws Exception {
		List<Integer> requestIds = List.of(start(1), start(2), start(3));
		this.interpreter.prepareBlocking(2);

		requestIds.forEach(this.requests::consent);

		assertThat(this.interpreter.awaitBlockingCalls(2, TimeUnit.SECONDS)).isTrue();
		assertThat(this.interpreter.getCallCount()).isEqualTo(2);
		assertThat(this.interpreter.getMaximumActiveCalls()).isEqualTo(2);
		assertThat(this.interpretationExecutor).isInstanceOfSatisfying(ThreadPoolTaskExecutor.class,
				pool -> assertThat(pool.getThreadPoolExecutor().getQueue()).hasSize(1));

		this.interpreter.releaseBlockingCalls();
		requestIds.forEach(id -> awaitState(id, "INTERPRETED"));

		assertThat(this.interpreter.getCallCount()).isEqualTo(3);
		assertThat(this.interpreter.getMaximumActiveCalls()).isEqualTo(2);
	}

	@Test
	void uc3Extension3a_concurrentRepeatedConsentStartsOneJobAndOneVersion() throws Exception {
		int requestId = start(1);
		this.interpreter.prepareBlocking(1);

		List<Boolean> results = race(() -> consent(requestId), () -> consent(requestId));

		assertThat(results).containsExactlyInAnyOrder(true, false);
		assertThat(this.interpreter.awaitBlockingCalls(2, TimeUnit.SECONDS)).isTrue();
		assertThat(this.interpreter.getCallCount()).isOne();
		this.interpreter.releaseBlockingCalls();
		awaitState(requestId, "INTERPRETED");
		assertThat(this.jdbc.queryForObject("select count(*) from interpretations where request_id = ?", Integer.class,
				requestId))
			.isOne();
	}

	private int start(int petId) {
		return this.requests.startForOwner(petId, "Request for pet " + petId).request().getId();
	}

	private boolean consent(int requestId) {
		try {
			this.requests.consent(requestId);
			return true;
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	private List<Boolean> race(Callable<Boolean> first, Callable<Boolean> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Boolean> firstResult = executor.submit(awaitStart(first, ready, start));
			Future<Boolean> secondResult = executor.submit(awaitStart(second, ready, start));
			assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			return List.of(firstResult.get(5, TimeUnit.SECONDS), secondResult.get(5, TimeUnit.SECONDS));
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Callable<Boolean> awaitStart(Callable<Boolean> action, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			assertThat(start.await(2, TimeUnit.SECONDS)).isTrue();
			return action.call();
		};
	}

	private void awaitState(int requestId, String expectedState) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		String state;
		do {
			state = this.jdbc.queryForObject("select state from scheduling_requests where id = ?", String.class,
					requestId);
			if (expectedState.equals(state)) {
				return;
			}
			Thread.onSpinWait();
		}
		while (System.nanoTime() < deadline);
		assertThat(state).isEqualTo(expectedState);
	}

}
