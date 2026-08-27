package org.springframework.samples.petclinic.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.AuditRecord;
import org.springframework.samples.petclinic.scheduling.repository.AuditRecordRepository;

@ExtendWith(MockitoExtension.class)
class AuditServiceTests {

	private static final Instant FIXED_NOW = Instant.parse("2026-01-15T12:00:00Z");

	@Mock
	private AuditRecordRepository auditRecordRepository;

	private AuditService auditService;

	@BeforeEach
	void setUp() {
		auditService = new AuditService(auditRecordRepository, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
	}

	@Test
	void recordEventPersistsAllowlistedMetadataOnly() {
		when(auditRecordRepository.save(any(AuditRecord.class))).thenAnswer(invocation -> {
			AuditRecord record = invocation.getArgument(0);
			record.setId(1);
			return record;
		});

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("username", "owner1");
		metadata.put("password", "secret-password");
		metadata.put("rawText", "My dog is sick");
		metadata.put("originalText", "sensitive intake");
		metadata.put("clinicalNotes", "diagnosis detail");
		metadata.put("notes", "private");
		metadata.put("description", "should not leak");
		metadata.put("failedAttempts", 5);
		metadata.put("role", "OWNER");

		AuditRecord saved = auditService.recordEvent(AuditEventType.LOGIN_FAILED, "owner1", "OWNER", "ACCOUNT", "42",
				"INVALID_CREDENTIALS", metadata);

		ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
		verify(auditRecordRepository).save(captor.capture());
		AuditRecord persisted = captor.getValue();

		assertThat(persisted.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILED);
		assertThat(persisted.getOccurredAt()).isEqualTo(FIXED_NOW);
		assertThat(persisted.getMetadataJson()).contains("\"username\":\"owner1\"");
		assertThat(persisted.getMetadataJson()).contains("\"failedAttempts\":5");
		assertThat(persisted.getMetadataJson()).contains("\"role\":\"OWNER\"");
		assertThat(persisted.getMetadataJson()).doesNotContain("secret-password");
		assertThat(persisted.getMetadataJson()).doesNotContain("My dog is sick");
		assertThat(persisted.getMetadataJson()).doesNotContain("sensitive intake");
		assertThat(persisted.getMetadataJson()).doesNotContain("diagnosis detail");
		assertThat(persisted.getMetadataJson()).doesNotContain("password");
		assertThat(persisted.getMetadataJson()).doesNotContain("rawText");
		assertThat(persisted.getMetadataJson()).doesNotContain("clinicalNotes");
		assertThat(saved.getId()).isEqualTo(1);
	}

	@Test
	void sanitizeAndSerializeMetadataEscapesStrings() {
		Map<String, Object> metadata = Map.of("note", "line\"with\\quotes");
		// "note" is disallowed; use a safe key
		metadata = Map.of("reason", "line\"with\\quotes");

		String json = auditService.sanitizeAndSerializeMetadata(metadata);

		assertThat(json).isEqualTo("{\"reason\":\"line\\\"with\\\\quotes\"}");
	}

	@Test
	void sanitizeDropsDisallowedKeysCaseInsensitively() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("Password", "x");
		metadata.put("ORIGINALTEXT", "y");
		metadata.put("ok", true);

		String json = auditService.sanitizeAndSerializeMetadata(metadata);

		assertThat(json).isEqualTo("{\"ok\":true}");
	}

}
