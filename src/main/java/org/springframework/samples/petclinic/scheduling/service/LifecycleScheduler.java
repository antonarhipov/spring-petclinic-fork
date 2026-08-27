package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "petclinic.scheduling.lifecycle.enabled", havingValue = "true", matchIfMissing = true)
public class LifecycleScheduler {

	private static final Logger log = LoggerFactory.getLogger(LifecycleScheduler.class);

	private final LifecycleProcessor lifecycleProcessor;

	private final SchedulingTimeService timeService;

	private volatile Instant lastRunTime;

	private volatile long lastRunDurationMs = 0;

	private volatile String lastError = null;

	private final AtomicLong totalRunsCount = new AtomicLong(0);

	private volatile boolean healthy = true;

	public LifecycleScheduler(LifecycleProcessor lifecycleProcessor, SchedulingTimeService timeService) {
		this.lifecycleProcessor = lifecycleProcessor;
		this.timeService = timeService;
	}

	@Scheduled(fixedDelayString = "${petclinic.scheduling.lifecycle.fixed-delay-ms:5000}")
	public void runScheduledCycle() {
		long startNano = System.nanoTime();
		Instant now = timeService.now();
		try {
			lifecycleProcessor.processOverdueDeadlines();
			this.lastRunTime = now;
			this.lastRunDurationMs = (System.nanoTime() - startNano) / 1_000_000;
			this.lastError = null;
			this.healthy = true;
			this.totalRunsCount.incrementAndGet();
		}
		catch (Exception e) {
			this.lastRunTime = now;
			this.lastRunDurationMs = (System.nanoTime() - startNano) / 1_000_000;
			this.lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
			this.healthy = false;
			this.totalRunsCount.incrementAndGet();
			log.error("Unhandled error during scheduled lifecycle cycle", e);
		}
	}

	public Instant getLastRunTime() {
		return lastRunTime;
	}

	public long getLastRunDurationMs() {
		return lastRunDurationMs;
	}

	public String getLastError() {
		return lastError;
	}

	public long getTotalRunsCount() {
		return totalRunsCount.get();
	}

	public boolean isHealthy() {
		return healthy;
	}

}
