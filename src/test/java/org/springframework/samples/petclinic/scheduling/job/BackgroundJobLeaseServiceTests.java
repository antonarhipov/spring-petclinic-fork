package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackgroundJobLeaseServiceTests {

	@Mock
	private BackgroundJobRepository jobRepository;

	private final Clock clock = Clock.fixed(Instant.parse("2026-08-31T09:00:00Z"), ZoneOffset.UTC);

	private BackgroundJobLeaseService leaseService;

	@BeforeEach
	void setUp() {
		this.leaseService = new BackgroundJobLeaseService(this.jobRepository, this.clock);
	}

	@Test
	void claimCreatesShortLeaseBeforeWorkerReceivesJob() {
		BackgroundJob job = job(JobState.PENDING, 0);
		when(this.jobRepository.findAvailableJobsForLease(this.clock.instant())).thenReturn(List.of(job));
		when(this.jobRepository.save(job)).thenReturn(job);

		BackgroundJobLeaseService.JobLease lease = this.leaseService.claimNext().orElseThrow();

		assertThat(job.getState()).isEqualTo(JobState.RUNNING);
		assertThat(job.getLeaseToken()).isEqualTo(lease.leaseToken()).isNotBlank();
		assertThat(job.getLeaseUntil()).isEqualTo(this.clock.instant().plusSeconds(30));
		assertThat(lease.commandId()).isEqualTo(job.getCommandId()).isNotNull();
		assertThat(lease.expectedVersion()).isEqualTo(job.getVersion());
	}

	@Test
	void expiredLeaseRecoveryIncrementsAndCapsAttempts() {
		BackgroundJob job = job(JobState.RUNNING, 0);
		when(this.jobRepository.findExpiredLeasedJobs(this.clock.instant())).thenReturn(List.of(job));

		this.leaseService.recoverExpiredLeases();
		assertThat(job.getAttemptCount()).isEqualTo(1);
		assertThat(job.getState()).isEqualTo(JobState.PENDING);

		job.setState(JobState.RUNNING);
		job.setLeaseToken("second");
		this.leaseService.recoverExpiredLeases();
		assertThat(job.getAttemptCount()).isEqualTo(2);
		assertThat(job.getState()).isEqualTo(JobState.PENDING);

		job.setState(JobState.RUNNING);
		job.setLeaseToken("third");
		this.leaseService.recoverExpiredLeases();
		assertThat(job.getAttemptCount()).isEqualTo(3);
		assertThat(job.getState()).isEqualTo(JobState.FAILED);
		assertThat(job.getOutcomeCategory()).isEqualTo(OutcomeCategory.EXHAUSTED_RETRIES);
	}

	@Test
	void staleWorkerCannotFailReplacementLease() {
		BackgroundJob job = job(JobState.RUNNING, 1);
		job.setLeaseToken("replacement");
		when(this.jobRepository.findById(1L)).thenReturn(Optional.of(job));

		this.leaseService.markFailed(1L, "stale-token");

		assertThat(job.getState()).isEqualTo(JobState.RUNNING);
		verify(this.jobRepository, never()).save(any());
	}

	private BackgroundJob job(JobState state, int attemptCount) {
		BackgroundJob job = new BackgroundJob(JobType.INTERPRETATION, null, null, state, this.clock.instant());
		job.setId(1L);
		job.setAttemptCount(attemptCount);
		job.setLeaseToken("lease");
		job.setLeaseUntil(this.clock.instant().minusSeconds(1));
		job.setVersion(4L);
		return job;
	}

}
