package org.springframework.samples.petclinic.shared.command;

import java.util.UUID;

/**
 * Supplies authenticated browser-command context without coupling shared command
 * infrastructure to the security or audit capabilities.
 */
public interface BrowserCommandContext {

	Long currentActorAccountId();

	void recordStructuredEvent(Long actorAccountId, String action, String targetType, String targetId, String outcome,
			UUID commandId, Object beforeSnapshot, Object afterSnapshot);

}
