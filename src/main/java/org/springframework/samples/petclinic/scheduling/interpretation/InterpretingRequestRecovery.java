package org.springframework.samples.petclinic.scheduling.interpretation;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.samples.petclinic.scheduling.request.RequestService;
import org.springframework.samples.petclinic.scheduling.request.RequestState;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class InterpretingRequestRecovery {

	private final SchedulingRequestRepository requests;

	private final RequestService requestService;

	public InterpretingRequestRecovery(SchedulingRequestRepository requests, RequestService requestService) {
		this.requests = requests;
		this.requestService = requestService;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void recover() {
		this.requests.findAll()
			.stream()
			.filter(request -> request.getState() == RequestState.INTERPRETING)
			.forEach(request -> this.requestService.aiUnavailable(request.getId()));
	}

}
