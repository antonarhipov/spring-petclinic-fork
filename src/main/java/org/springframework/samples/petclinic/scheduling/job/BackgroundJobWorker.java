package org.springframework.samples.petclinic.scheduling.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobLeaseService.JobLease;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationJobCoordinator;
import org.springframework.samples.petclinic.scheduling.matching.MatchingJobCoordinator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackgroundJobWorker {

	private static final Logger log = LoggerFactory.getLogger(BackgroundJobWorker.class);

	private final BackgroundJobLeaseService leaseService;

	private final InterpretationJobCoordinator interpretationJobCoordinator;

	private final MatchingJobCoordinator matchingJobCoordinator;

	public BackgroundJobWorker(BackgroundJobLeaseService leaseService,
			InterpretationJobCoordinator interpretationJobCoordinator, MatchingJobCoordinator matchingJobCoordinator) {
		this.leaseService = leaseService;
		this.interpretationJobCoordinator = interpretationJobCoordinator;
		this.matchingJobCoordinator = matchingJobCoordinator;
	}

	@Scheduled(fixedDelay = 1000)
	public void pollAndProcessJobs() {
		try {
			recoverExpiredLeases();
			processNextJob();
		}
		catch (Exception ex) {
			log.error("Background job polling failed; category={}", ex.getClass().getSimpleName());
		}
	}

	public boolean processNextJob() {
		JobLease lease = this.leaseService.claimNext().orElse(null);
		if (lease == null) {
			return false;
		}
		log.info("Claimed lease for job {} (type: {})", lease.jobId(), lease.jobType());

		try {
			if (lease.jobType() == JobType.INTERPRETATION) {
				this.interpretationJobCoordinator.executeInterpretationJob(lease.jobId(), lease.leaseToken());
			}
			else if (lease.jobType() == JobType.MATCHING) {
				this.matchingJobCoordinator.executeMatchingJob(lease.jobId(), lease.leaseToken());
			}
		}
		catch (Exception ex) {
			log.error("Background job {} failed; category={}", lease.jobId(), ex.getClass().getSimpleName());
			this.leaseService.markFailed(lease.jobId(), lease.leaseToken());
		}
		return true;
	}

	public void recoverExpiredLeases() {
		this.leaseService.recoverExpiredLeases();
	}

}
