package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the short database transactions used to lease and recover background work.
 * External AI and solver execution happens only after these methods have committed.
 */
@Service
public class BackgroundJobLeaseService {

	private static final Logger log = LoggerFactory.getLogger(BackgroundJobLeaseService.class);

	private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

	private static final int MAX_LEASE_RECOVERIES = 3;

	private final BackgroundJobRepository jobRepository;

	private final Clock clock;

	public BackgroundJobLeaseService(BackgroundJobRepository jobRepository, Clock clock) {
		this.jobRepository = jobRepository;
		this.clock = clock;
	}

	@Transactional
	public Optional<JobLease> claimNext() {
		Instant now = this.clock.instant();
		List<BackgroundJob> available = this.jobRepository.findAvailableJobsForLease(now);
		if (available.isEmpty()) {
			return Optional.empty();
		}

		BackgroundJob job = available.get(0);
		String leaseToken = UUID.randomUUID().toString();
		job.setLeaseToken(leaseToken);
		job.setLeaseUntil(now.plus(LEASE_DURATION));
		job.setState(JobState.RUNNING);
		job.setStartedAt(now);
		this.jobRepository.save(job);
		return Optional
			.of(new JobLease(job.getId(), job.getJobType(), leaseToken, job.getCommandId(), job.getVersion()));
	}

	@Transactional
	public void recoverExpiredLeases() {
		Instant now = this.clock.instant();
		for (BackgroundJob job : this.jobRepository.findExpiredLeasedJobs(now)) {
			int recoveryCount = (job.getAttemptCount() != null ? job.getAttemptCount() : 0) + 1;
			job.setAttemptCount(recoveryCount);
			log.warn("Recovering expired lease for job {} (recovery attempt {})", job.getId(), recoveryCount);
			if (recoveryCount >= MAX_LEASE_RECOVERIES) {
				job.setState(JobState.FAILED);
				job.setOutcomeCategory(OutcomeCategory.EXHAUSTED_RETRIES);
				job.setCompletedAt(now);
			}
			else {
				job.setState(JobState.PENDING);
				job.setLeaseToken(null);
				job.setLeaseUntil(null);
			}
			this.jobRepository.save(job);
		}
	}

	@Transactional
	public void markFailed(Long jobId, String leaseToken) {
		this.jobRepository.findById(jobId).ifPresent(job -> {
			if (job.getState() != JobState.RUNNING || !Objects.equals(job.getLeaseToken(), leaseToken)) {
				return;
			}
			job.setState(JobState.FAILED);
			job.setOutcomeCategory(OutcomeCategory.ERROR);
			job.setCompletedAt(this.clock.instant());
			this.jobRepository.save(job);
		});
	}

	public record JobLease(Long jobId, JobType jobType, String leaseToken, UUID commandId, Long expectedVersion) {
	}

}
