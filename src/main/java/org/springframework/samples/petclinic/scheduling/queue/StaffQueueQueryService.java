package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffQueueQueryService {

	private final StaffQueueRepository queue;

	public StaffQueueQueryService(StaffQueueRepository queue) {
		this.queue = queue;
	}

	@Transactional(readOnly = true)
	public List<StaffQueueItem> activeItems() {
		return this.queue.findByStateNotOrderByPriorityAscIdAsc(QueueState.CLOSED);
	}

}
