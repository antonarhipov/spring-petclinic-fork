package org.springframework.samples.petclinic.scheduling.matching;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.samples.petclinic.availability.AvailabilityConflictException;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJob;
import org.springframework.samples.petclinic.scheduling.job.BackgroundJobRepository;
import org.springframework.samples.petclinic.scheduling.job.JobState;
import org.springframework.samples.petclinic.scheduling.job.OutcomeCategory;
import org.springframework.samples.petclinic.scheduling.offer.Offer;
import org.springframework.samples.petclinic.scheduling.offer.OfferOrigin;
import org.springframework.samples.petclinic.scheduling.offer.OfferService;
import org.springframework.samples.petclinic.scheduling.queue.StaffFallbackPort;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevisionRepository;
import org.springframework.stereotype.Component;

@Component
public class MatchingJobCoordinator {

	private static final Logger log = LoggerFactory.getLogger(MatchingJobCoordinator.class);

	private final MatchingSnapshotFactory snapshotFactory;

	private final AppointmentSchedulingSolver solver;

	private final OfferService offerService;

	private final StaffFallbackPort staffFallbackPort;

	private final BackgroundJobRepository jobRepository;

	private final SchedulingRequestRepository requestRepository;

	private final WorkflowRevisionRepository workflowRevisionRepository;

	private final Clock clock;

	public MatchingJobCoordinator(MatchingSnapshotFactory snapshotFactory, AppointmentSchedulingSolver solver,
			OfferService offerService, StaffFallbackPort staffFallbackPort, BackgroundJobRepository jobRepository,
			SchedulingRequestRepository requestRepository, WorkflowRevisionRepository workflowRevisionRepository,
			Clock clock) {
		this.snapshotFactory = snapshotFactory;
		this.solver = solver;
		this.offerService = offerService;
		this.staffFallbackPort = staffFallbackPort;
		this.jobRepository = jobRepository;
		this.requestRepository = requestRepository;
		this.workflowRevisionRepository = workflowRevisionRepository;
		this.clock = clock;
	}

	public void executeMatchingJob(BackgroundJob job) {
		WorkflowRevision workflowRevision = job.getWorkflowRevision();
		if (workflowRevision == null) {
			log.warn("Job {} has no associated workflow revision", job.getId());
			updateJobState(job, JobState.FAILED, OutcomeCategory.ERROR, null);
			return;
		}

		if (workflowRevision.getId() != null) {
			workflowRevision = this.workflowRevisionRepository.findById(workflowRevision.getId())
				.orElse(workflowRevision);
		}

		SchedulingRequest request = workflowRevision.getRequest();
		if (request != null && request.getId() != null) {
			request = this.requestRepository.findById(request.getId()).orElse(request);
		}

		if (request == null || request.getCurrentWorkflowRevision() == null
				|| !request.getCurrentWorkflowRevision().getId().equals(workflowRevision.getId())
				|| request.getState().isTerminal()) {
			log.info("Job {} is stale for request {}", job.getId(), (request != null ? request.getId() : "null"));
			updateJobState(job, JobState.STALE, null, null);
			return;
		}

		int maxRetries = 1;
		boolean success = false;
		while (!success && job.getCalendarRetryCount() <= maxRetries) {
			MatchingSnapshotFactory.SnapshotResult snapshot = this.snapshotFactory.buildSnapshot(workflowRevision);
			if (snapshot.candidateSlots().isEmpty()) {
				log.info("No candidates found for workflow revision {}", workflowRevision.getId());
				updateJobState(job, JobState.SUCCEEDED, OutcomeCategory.NO_MATCH, null);

				this.staffFallbackPort.sendToFallbackQueue(request.getId(), "NO_MATCH", workflowRevision.getUrgency(),
						"No eligible slot found matching constraints");
				return;
			}

			Optional<CandidateSlot> chosenSlot = this.solver.solve(snapshot.candidateSlots());
			if (chosenSlot.isEmpty()) {
				log.info("Solver could not match candidate slot for workflow revision {}", workflowRevision.getId());
				updateJobState(job, JobState.SUCCEEDED, OutcomeCategory.NO_MATCH, null);

				this.staffFallbackPort.sendToFallbackQueue(request.getId(), "NO_MATCH", workflowRevision.getUrgency(),
						"Solver failed to schedule valid candidate");
				return;
			}

			try {
				CandidateSlot slot = chosenSlot.get();
				String explanation = "Timefold matched slot with vet %s starting at %s".formatted(slot.getVetName(),
						slot.getStartAt());
				Offer offer = this.offerService.createHeldOffer(request, workflowRevision, slot, OfferOrigin.AUTOMATIC,
						snapshot.calendarRevision(), explanation);

				updateJobState(job, JobState.SUCCEEDED, OutcomeCategory.SUCCESS, snapshot.calendarRevision());
				success = true;
			}
			catch (AvailabilityConflictException | ObjectOptimisticLockingFailureException ex) {
				log.warn("Calendar changed during solve for job {}, retry count {}", job.getId(),
						job.getCalendarRetryCount());
				job.setCalendarRetryCount(job.getCalendarRetryCount() + 1);
				if (job.getCalendarRetryCount() > maxRetries) {
					updateJobState(job, JobState.SUCCEEDED, OutcomeCategory.CALENDAR_CHANGED, null);

					this.staffFallbackPort.sendToFallbackQueue(request.getId(), "CALENDAR_CHANGED",
							workflowRevision.getUrgency(), "Calendar conflict during matching attempt");
					return;
				}
			}
		}
	}

	private void updateJobState(BackgroundJob job, JobState state, OutcomeCategory outcome, Long calendarRevision) {
		job.setState(state);
		job.setOutcomeCategory(outcome);
		if (calendarRevision != null) {
			job.setCalendarRevision(calendarRevision);
		}
		job.setCompletedAt(this.clock.instant());
		if (job.getId() != null) {
			this.jobRepository.findById(job.getId()).ifPresentOrElse(managed -> {
				managed.setState(job.getState());
				managed.setOutcomeCategory(job.getOutcomeCategory());
				managed.setCalendarRevision(job.getCalendarRevision());
				managed.setCalendarRetryCount(job.getCalendarRetryCount());
				managed.setCompletedAt(job.getCompletedAt());
				this.jobRepository.save(managed);
			}, () -> this.jobRepository.save(job));
		}
		else {
			this.jobRepository.save(job);
		}
	}

}
