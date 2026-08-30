package org.springframework.samples.petclinic.scheduling.queue;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FallbackRoutingService {

	private final StaffQueueRepository queueItems;

	private final Clock clock;

	public FallbackRoutingService(StaffQueueRepository queueItems, Clock clock) {
		this.queueItems = queueItems;
		this.clock = clock;
	}

	@Transactional
	public StaffQueueItem ensureQueueItem(Long requestId, String reasonCode, boolean emergency) {
		Instant now = Instant.now(this.clock);
		return this.queueItems.findByRequestId(requestId).map(item -> {
			if (item.getState() == QueueState.CLOSED || item.getState() == QueueState.RESOLVED) {
				item.setState(QueueState.NEW);
			}
			if (emergency) {
				item.setPriority("EMERGENCY");
			}
			item.setReasonCode(reasonCode);
			item.setUpdatedAt(now);
			return item;
		}).orElseGet(() -> {
			StaffQueueItem item = new StaffQueueItem();
			item.setRequestId(requestId);
			item.setPriority(emergency ? "EMERGENCY" : "NORMAL");
			item.setState(QueueState.NEW);
			item.setReasonCode(reasonCode);
			item.setCreatedAt(now);
			item.setUpdatedAt(now);
			return this.queueItems.save(item);
		});
	}

}
