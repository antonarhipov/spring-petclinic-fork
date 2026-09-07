package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HeldSlotInvalidationService {

	private final RequestService requestService;

	public HeldSlotInvalidationService(RequestService requestService) {
		this.requestService = requestService;
	}

	@Transactional
	public void invalidateHeldSlot(int requestId) {
		this.requestService.scheduleChanged(requestId);
	}

}
