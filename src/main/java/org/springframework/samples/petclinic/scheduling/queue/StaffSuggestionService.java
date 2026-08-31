package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.samples.petclinic.scheduling.matching.AppointmentSchedulingSolver;
import org.springframework.samples.petclinic.scheduling.matching.CandidateSlot;
import org.springframework.samples.petclinic.scheduling.matching.MatchingSnapshotFactory;
import org.springframework.samples.petclinic.scheduling.request.WorkflowRevision;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffSuggestionService {

	private final QueueItemRepository queueItemRepository;

	private final MatchingSnapshotFactory snapshotFactory;

	private final AppointmentSchedulingSolver solver;

	public StaffSuggestionService(QueueItemRepository queueItemRepository, MatchingSnapshotFactory snapshotFactory,
			AppointmentSchedulingSolver solver) {
		this.queueItemRepository = queueItemRepository;
		this.snapshotFactory = snapshotFactory;
		this.solver = solver;
	}

	public List<CandidateSlot> rankedSuggestions(Long queueItemId, Long actorAccountId) {
		QueueItem queueItem = this.queueItemRepository.findById(queueItemId)
			.orElseThrow(() -> new IllegalArgumentException("Queue item not found: " + queueItemId));
		queueItem.requireAssignedTo(actorAccountId);
		WorkflowRevision revision = queueItem.getWorkflowRevision() != null ? queueItem.getWorkflowRevision()
				: queueItem.getRequest().getCurrentWorkflowRevision();
		if (revision == null) {
			return List.of();
		}
		return this.solver.rank(this.snapshotFactory.buildSnapshot(revision).candidateSlots(), 5);
	}

}
