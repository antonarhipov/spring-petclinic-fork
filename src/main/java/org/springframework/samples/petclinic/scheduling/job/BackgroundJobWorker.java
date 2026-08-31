package org.springframework.samples.petclinic.scheduling.job;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationJobCoordinator;
import org.springframework.samples.petclinic.scheduling.matching.MatchingJobCoordinator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BackgroundJobWorker {

	private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);

	private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

	private final BackgroundJobRepository jobRepository;

	private final InterpretationJobCoordinator interpretationJobCoordinator;

	private final MatchingJobCoordinator matchingJobCoordinator;

	private final Clock clock;

	public BackgroundJobWorker(BackgroundJobRepository jobRepository,
			InterpretationJobCoordinator interpretationJobCoordinator, MatchingJobCoordinator matchingJobCoordinator,
			Clock clock) {
		this.jobRepository = jobRepository;
		this.interpretationJobCoordinator = interpretationJobCoordinator;
		this.matchingJobCoordinator = matchingJobCoordinator;
		this.clock = clock;
	}

	@Transactional
	@Scheduled(fixedDelay = 1000)
	public void pollAndProcessJobs() {
		try {
			recoverExpiredLeases();
			processNextJob();
		}
		catch (Exception ex) {
			log.error("Error during background job worker polling: {}", ex.getMessage(), ex);
		}
	}

	@Transactional
	public boolean processNextJob() {
		Instant now = this.clock.instant();
		List<BackgroundJob> available = this.jobRepository.findAvailableJobsForLease(now);
		if (available.isEmpty()) {
			return false;
		}

		BackgroundJob job = available.get(0);
		String leaseToken = UUID.randomUUID().toString();
		job.setLeaseToken(leaseToken);
		job.setLeaseUntil(now.plus(LEASE_DURATION));
		job.setState(JobState.RUNNING);
		job.setStartedAt(now);
		BackgroundJob leasedJob = this.jobRepository.save(job);

		log.info("Claimed lease for job {} (type: {})", leasedJob.getId(), leasedJob.getJobType());

		try {
			if (leasedJob.getJobType() == JobType.INTERPRETATION) {
				this.interpretationJobCoordinator.executeInterpretationJob(leasedJob);
			}
			else if (leasedJob.getJobType() == JobType.MATCHING) {
				this.matchingJobCoordinator.executeMatchingJob(leasedJob);
			}
		}
		catch (Exception ex) {
			log.error("Error executing background job {}: {}", leasedJob.getId(), ex.getMessage(), ex);
			leasedJob.setState(JobState.FAILED);
			leasedJob.setOutcomeCategory(OutcomeCategory.ERROR);
			leasedJob.setCompletedAt(this.clock.instant());
			this.jobRepository.save(leasedJob);
		}
		return true;
	}

	@Transactional
	public void recoverExpiredLeases() {
		Instant now = this.clock.instant();
		List<BackgroundJob> expired = this.jobRepository.findExpiredLeasedJobs(now);
		for (BackgroundJob job : expired) {
			log.warn("Recovering expired lease for job {} (attempt: {})", job.getId(), job.getAttemptCount());
			if (job.getAttemptCount() >= 3) {
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

}
