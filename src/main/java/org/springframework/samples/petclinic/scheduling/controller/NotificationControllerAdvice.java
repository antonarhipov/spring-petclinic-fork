package org.springframework.samples.petclinic.scheduling.controller;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NotificationControllerAdvice {

	private final NotificationService notificationService;

	public NotificationControllerAdvice(ObjectProvider<NotificationService> notificationServiceProvider) {
		this.notificationService = notificationServiceProvider.getIfAvailable();
	}

	@ModelAttribute("unreadNotificationCount")
	public long unreadNotificationCount() {
		if (this.notificationService == null) {
			return 0L;
		}
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
			if (userPrincipal.getRole() == AccountRole.OWNER && userPrincipal.getOwnerId() != null) {
				return this.notificationService.countUnread(userPrincipal.getOwnerId());
			}
		}
		return 0L;
	}

}
