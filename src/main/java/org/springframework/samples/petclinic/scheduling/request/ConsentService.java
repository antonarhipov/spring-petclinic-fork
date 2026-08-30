package org.springframework.samples.petclinic.scheduling.request;

import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ConsentService {

	private final RequestWorkflowService workflow;

	public ConsentService(RequestWorkflowService workflow) {
		this.workflow = workflow;
	}

	public UUID agree(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		return this.workflow.agreeToInterpret(requestId, ownerId, accountId, expectedVersion);
	}

	public void decline(Long requestId, Integer ownerId, Long accountId, Integer expectedVersion) {
		this.workflow.declineInterpretation(requestId, ownerId, accountId, expectedVersion);
	}

}
