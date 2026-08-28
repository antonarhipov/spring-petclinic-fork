package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.request.RequestRevision;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestState;

@Service
public class StaffInterpretationService {

	private final StaffQueueRepository queue;

	private final Clock clock;

	public StaffInterpretationService(StaffQueueRepository queue, Clock clock) {
		this.queue = queue;
		this.clock = clock;
	}

	@Transactional
	public void complete(Integer queueItemId, String reason, int duration, String careType, String specialty,
			Authentication actor) {
		StaffQueueItem item = this.queue.findById(queueItemId).orElseThrow();
		if (item.getClaimedBy() == null || !item.getClaimedBy().getUsername().equals(actor.getName())) {
			throw new IllegalStateException("Claim the queue item before working it");
		}
		RequestRevision revision = item.getRequest().getCurrentRevision();
		revision.applyInterpretation("staff-entered", "staff", reason, duration, careType, specialty, null, "STANDARD");
		revision.confirm(this.clock.instant());
		item.getRequest().moveTo(SchedulingRequestState.READY_FOR_SUGGESTION);
	}

}
