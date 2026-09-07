package org.springframework.samples.petclinic.scheduling.request;

import java.security.Principal;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestStatusController {

	private final AuthenticatedOwnerService owners;

	private final SchedulingRequestRepository requests;

	public RequestStatusController(AuthenticatedOwnerService owners, SchedulingRequestRepository requests) {
		this.owners = owners;
		this.requests = requests;
	}

	@GetMapping("/my/requests/{id}/status")
	Map<String, String> status(@PathVariable int id, Principal principal) {
		int ownerId = this.owners.requireOwner(principal).getId();
		SchedulingRequest request = this.requests.findByIdAndPetOwnerId(id, ownerId)
			.orElseThrow(OwnerResourceNotFoundException::new);
		return Map.of("state", request.getState().name());
	}

}
