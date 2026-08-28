package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.security.OwnerAccessService;

@Service
public class SchedulingRequestQueryService {

	private final SchedulingRequestRepository requests;

	private final OwnerAccessService owners;

	public SchedulingRequestQueryService(SchedulingRequestRepository requests, OwnerAccessService owners) {
		this.requests = requests;
		this.owners = owners;
	}

	@Transactional(readOnly = true)
	public SchedulingRequest ownedRequest(Integer requestId, Authentication actor) {
		return this.requests.findOwnedById(requestId, this.owners.currentOwner(actor).owner().getId())
			.orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
	}

	@Transactional(readOnly = true)
	public List<SchedulingRequest> history(Authentication actor) {
		return this.requests.findOwnedHistory(this.owners.currentOwner(actor).owner().getId());
	}

}
