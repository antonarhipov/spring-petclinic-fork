package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.scheduling.model.Notification;
import org.springframework.samples.petclinic.scheduling.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

	private static final Instant FIXED_NOW = Instant.parse("2026-01-15T12:00:00Z");

	@Mock
	private NotificationRepository notificationRepository;

	private NotificationService notificationService;

	@BeforeEach
	void setUp() {
		notificationService = new NotificationService(notificationRepository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
	}

	@Test
	void sendNotificationStoresKeyedMessageWithoutEmbeddingSecrets() {
		Owner owner = new Owner();
		owner.setId(1);
		when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
			Notification n = invocation.getArgument(0);
			n.setId(9);
			return n;
		});

		Notification saved = notificationService.sendNotification(owner, "notification.offer.title",
				"notification.offer.message", "{\"slot\":\"2026-01-20T10:00:00Z\"}", "/scheduling/requests/3");

		ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepository).save(captor.capture());
		Notification persisted = captor.getValue();

		assertThat(persisted.getTitleKey()).isEqualTo("notification.offer.title");
		assertThat(persisted.getMessageKey()).isEqualTo("notification.offer.message");
		assertThat(persisted.getTargetUrl()).isEqualTo("/scheduling/requests/3");
		assertThat(persisted.isRead()).isFalse();
		assertThat(persisted.getCreatedAt()).isEqualTo(FIXED_NOW);
		assertThat(persisted.getOwner()).isSameAs(owner);
		assertThat(saved.getId()).isEqualTo(9);
	}

	@Test
	void markAsReadOnlyForOwningOwner() {
		Notification notification = new Notification();
		notification.setId(3);
		notification.setRead(false);
		when(notificationRepository.findByIdAndOwnerId(3, 1)).thenReturn(Optional.of(notification));
		when(notificationRepository.findByIdAndOwnerId(3, 2)).thenReturn(Optional.empty());
		when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

		assertThat(notificationService.markAsRead(3, 1)).isTrue();
		assertThat(notification.isRead()).isTrue();
		assertThat(notificationService.markAsRead(3, 2)).isFalse();
	}

}
