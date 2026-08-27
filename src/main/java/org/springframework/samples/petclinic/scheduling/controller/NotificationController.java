package org.springframework.samples.petclinic.scheduling.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.samples.petclinic.scheduling.dto.NotificationDto;
import org.springframework.samples.petclinic.scheduling.model.Notification;
import org.springframework.samples.petclinic.scheduling.repository.AppointmentRepository;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.samples.petclinic.scheduling.service.NotificationService;
import org.springframework.samples.petclinic.security.AccountRole;
import org.springframework.samples.petclinic.security.UserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class NotificationController {

	private static final Pattern REQUEST_PATTERN = Pattern.compile("^/(?:scheduling/)?requests/(\\d+).*");

	private static final Pattern APPOINTMENT_PATTERN = Pattern.compile("^/(?:scheduling/)?appointments/(\\d+).*");

	private final NotificationService notificationService;

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final AppointmentRepository appointmentRepository;

	public NotificationController(NotificationService notificationService,
			SchedulingRequestRepository schedulingRequestRepository, AppointmentRepository appointmentRepository) {
		this.notificationService = notificationService;
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.appointmentRepository = appointmentRepository;
	}

	@GetMapping({ "/scheduling/notifications", "/notifications" })
	public String listNotifications(@AuthenticationPrincipal UserPrincipal userPrincipal, Model model) {
		if (userPrincipal == null) {
			return "redirect:/login";
		}
		if (userPrincipal.getRole() != AccountRole.OWNER || userPrincipal.getOwnerId() == null) {
			throw new AccessDeniedException("Only pet owners can view notifications");
		}

		Integer ownerId = userPrincipal.getOwnerId();
		List<Notification> rawList = notificationService.findByOwner(ownerId);
		List<NotificationDto> dtoList = new ArrayList<>();

		for (Notification n : rawList) {
			boolean isAuthorized = checkTargetAuthorization(n.getTargetUrl(), ownerId);
			String effectiveUrl = isAuthorized ? normalizeTargetUrl(n.getTargetUrl()) : null;
			dtoList.add(new NotificationDto(n.getId(), n.getTitleKey(), n.getMessageKey(), n.getMessageParams(),
					effectiveUrl, n.isRead(), isAuthorized, n.getCreatedAt()));
		}

		model.addAttribute("notifications", dtoList);
		return "scheduling/notifications";
	}

	@PostMapping({ "/scheduling/notifications/{id}/read", "/notifications/{id}/read" })
	public String markAsRead(@PathVariable("id") Integer id, @AuthenticationPrincipal UserPrincipal userPrincipal) {
		if (userPrincipal == null || userPrincipal.getOwnerId() == null) {
			return "redirect:/login";
		}
		Integer ownerId = userPrincipal.getOwnerId();
		notificationService.markAsRead(id, ownerId);
		return "redirect:/scheduling/notifications";
	}

	private boolean checkTargetAuthorization(String targetUrl, Integer ownerId) {
		if (targetUrl == null || targetUrl.isBlank()) {
			return false;
		}

		Matcher reqMatcher = REQUEST_PATTERN.matcher(targetUrl);
		if (reqMatcher.find()) {
			try {
				int reqId = Integer.parseInt(reqMatcher.group(1));
				return schedulingRequestRepository.findByIdAndOwnerId(reqId, ownerId).isPresent();
			}
			catch (NumberFormatException e) {
				return false;
			}
		}

		Matcher apptMatcher = APPOINTMENT_PATTERN.matcher(targetUrl);
		if (apptMatcher.find()) {
			try {
				int apptId = Integer.parseInt(apptMatcher.group(1));
				return appointmentRepository.findByIdAndOwnerId(apptId, ownerId).isPresent();
			}
			catch (NumberFormatException e) {
				return false;
			}
		}

		return false;
	}

	private String normalizeTargetUrl(String targetUrl) {
		if (targetUrl == null) {
			return null;
		}
		if (targetUrl.startsWith("/requests/")) {
			return "/scheduling" + targetUrl;
		}
		if (targetUrl.startsWith("/appointments/")) {
			return "/scheduling" + targetUrl;
		}
		return targetUrl;
	}

}
