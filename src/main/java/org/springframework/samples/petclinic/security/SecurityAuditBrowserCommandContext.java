package org.springframework.samples.petclinic.security;

import java.util.UUID;

import org.springframework.samples.petclinic.audit.AuditService;
import org.springframework.samples.petclinic.shared.command.BrowserCommandContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditBrowserCommandContext implements BrowserCommandContext {

	private final AuditService auditService;

	public SecurityAuditBrowserCommandContext(AuditService auditService) {
		this.auditService = auditService;
	}

	@Override
	public Long currentActorAccountId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal principal
				? principal.getAccountId() : null;
	}

	@Override
	public void recordStructuredEvent(Long actorAccountId, String action, String targetType, String targetId,
			String outcome, UUID commandId, Object beforeSnapshot, Object afterSnapshot) {
		this.auditService.recordStructuredEvent(actorAccountId, action, targetType, targetId, outcome, null, commandId,
				beforeSnapshot, afterSnapshot);
	}

}
