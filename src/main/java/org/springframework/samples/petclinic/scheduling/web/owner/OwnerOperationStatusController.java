package org.springframework.samples.petclinic.scheduling.web.owner;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.samples.petclinic.account.Account;
import org.springframework.samples.petclinic.scheduling.request.OwnerOperationStatus;
import org.springframework.samples.petclinic.scheduling.request.OwnerOperationStatusService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OwnerOperationStatusController {

	private final CurrentOwnerAccount currentOwner;

	private final OwnerOperationStatusService statuses;

	public OwnerOperationStatusController(CurrentOwnerAccount currentOwner, OwnerOperationStatusService statuses) {
		this.currentOwner = currentOwner;
		this.statuses = statuses;
	}

	@GetMapping("/api/owner/scheduling-requests/{requestId}/operations/{operationId}")
	public ResponseEntity<OwnerOperationStatus> status(@PathVariable Long requestId, @PathVariable UUID operationId,
			Authentication authentication) {
		Account account = this.currentOwner.require(authentication);
		OwnerOperationStatus body = this.statuses.status(account.getOwnerId(), requestId, operationId);
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
	}

}
