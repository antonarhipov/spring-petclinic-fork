package org.springframework.samples.petclinic.scheduling.queue;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestState;

@Service
public class FallbackQueueService {

	private final StaffQueueRepository queue;

	public FallbackQueueService(StaffQueueRepository queue) {
		this.queue = queue;
	}

	@Transactional
	public StaffQueueItem route(SchedulingRequest request) {
		request.moveTo(SchedulingRequestState.STAFF_HANDLING);
		return this.queue.findByRequestId(request.getId())
			.orElseGet(() -> this.queue.save(new StaffQueueItem(request,
					request.isEmergencyPriority() ? QueuePriority.EMERGENCY : QueuePriority.STANDARD)));
	}

	@Transactional
	public void close(SchedulingRequest request, String details) {
		this.queue.findByRequestId(request.getId()).ifPresent(item -> item.close(details));
	}

}
