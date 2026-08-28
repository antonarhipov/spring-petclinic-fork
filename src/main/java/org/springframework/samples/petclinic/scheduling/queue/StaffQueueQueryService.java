package org.springframework.samples.petclinic.scheduling.queue;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;

@Service
public class StaffQueueQueryService {

	private final StaffQueueRepository queue;

	private final OwnerRepository owners;

	public StaffQueueQueryService(StaffQueueRepository queue, OwnerRepository owners) {
		this.queue = queue;
		this.owners = owners;
	}

	@Transactional(readOnly = true)
	public List<StaffQueueView> activeItems() {
		return this.queue.findByStateNotOrderByPriorityAscIdAsc(QueueState.CLOSED).stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	public StaffQueueView item(Integer itemId) {
		return view(this.queue.findDetailedById(itemId).orElseThrow());
	}

	private StaffQueueView view(StaffQueueItem item) {
		Owner owner = this.owners.findByPetId(item.getRequest().getPet().getId()).orElseThrow();
		return new StaffQueueView(item, owner);
	}

	public record StaffQueueView(StaffQueueItem item, Owner owner) {
	}

}
