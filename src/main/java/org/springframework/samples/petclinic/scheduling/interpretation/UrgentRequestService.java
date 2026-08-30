package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;

import org.springframework.samples.petclinic.scheduling.queue.FallbackRoutingService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrgentRequestService {

	private final FallbackRoutingService fallback;

	public UrgentRequestService(FallbackRoutingService fallback) {
		this.fallback = fallback;
	}

	@Transactional
	public void route(SchedulingRequest request, Instant now) {
		request.setSuspectedEmergency(true);
		request.setState(RequestState.STAFF_HANDLING);
		request.setOwnerStatusCode("STAFF_HANDLING");
		request.setUpdatedAt(now);
		this.fallback.ensureQueueItem(request.getId(), "EMERGENCY_SCREEN", true);
	}

}
