package org.springframework.samples.petclinic.scheduling.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.model.Notification;
import org.springframework.samples.petclinic.scheduling.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {

	private final NotificationRepository notificationRepository;

	private final Clock clock;

	public NotificationService(NotificationRepository notificationRepository, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.clock = clock;
	}

	public Notification sendNotification(Owner owner, String titleKey, String messageKey, String messageParams,
			String targetUrl) {
		Notification notification = new Notification();
		notification.setOwner(owner);
		notification.setTitleKey(titleKey);
		notification.setMessageKey(messageKey);
		notification.setMessageParams(messageParams);
		notification.setTargetUrl(targetUrl);
		notification.setRead(false);
		notification.setCreatedAt(Instant.now(clock));
		return notificationRepository.save(notification);
	}

	public Notification createNotification(Integer ownerId, String titleKey, String messageKey, String targetUrl) {
		if (ownerId == null) {
			return null;
		}
		Owner owner = new Owner();
		owner.setId(ownerId);
		return sendNotification(owner, titleKey, messageKey, "", targetUrl);
	}

	@Transactional(readOnly = true)
	public List<Notification> findByOwner(Integer ownerId) {
		return notificationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}

	@Transactional(readOnly = true)
	public long countUnread(Integer ownerId) {
		return notificationRepository.countByOwnerIdAndReadFalse(ownerId);
	}

	public boolean markAsRead(Integer notificationId, Integer ownerId) {
		Optional<Notification> opt = notificationRepository.findByIdAndOwnerId(notificationId, ownerId);
		if (opt.isPresent()) {
			Notification notification = opt.get();
			notification.setRead(true);
			notificationRepository.save(notification);
			return true;
		}
		return false;
	}

}
