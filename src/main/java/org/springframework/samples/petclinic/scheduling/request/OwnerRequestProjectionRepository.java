package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.stereotype.Repository;

@Repository
public class OwnerRequestProjectionRepository {

	private final SchedulingRequestRepository requests;

	public OwnerRequestProjectionRepository(SchedulingRequestRepository requests) {
		this.requests = requests;
	}

	public SchedulingRequest requireOwned(Long requestId, Integer ownerId) {
		return this.requests.findByIdAndOwnerId(requestId, ownerId).orElseThrow(OwnerResourceNotFoundException::new);
	}

}
