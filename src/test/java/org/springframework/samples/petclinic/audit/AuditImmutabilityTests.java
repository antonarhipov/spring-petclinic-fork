package org.springframework.samples.petclinic.audit;

import java.util.UUID;
import java.util.Map;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditImmutabilityTests {

	@Autowired
	private AuditService auditService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private ProtectedPayloadRepository protectedPayloadRepository;

	@Autowired
	private ProtectedPayloadService protectedPayloadService;

	@Test
	void structuredAuditSnapshotIsEncryptedAndRetainsBeforeAndAfterValues() {
		AuditEvent event = this.auditService.recordStructuredEvent(1L, "QUEUE_ITEM_REASSIGNED", "QueueItem", "42",
				"SUCCESS", UUID.randomUUID(), null, Map.of("assignee", 1, "version", 3),
				Map.of("assignee", 2, "version", 4));
		this.entityManager.flush();

		ProtectedPayload payload = this.protectedPayloadRepository.findById(event.getPayloadId()).orElseThrow();

		assertThat(this.protectedPayloadService.decryptToString(payload)).contains("\"before\"")
			.contains("\"after\"")
			.contains("\"assignee\":1")
			.contains("\"assignee\":2");
	}

	@Test
	void persistedAuditEventCannotBeUpdated() {
		AuditEvent event = this.auditService.recordEvent(1L, "TEST_ACTION", "TestTarget", "1", "SUCCESS",
				UUID.randomUUID(), null, null);
		this.entityManager.flush();
		event.setOutcome("ALTERED");

		assertThatThrownBy(this.entityManager::flush).isInstanceOf(IllegalStateException.class)
			.hasMessage("Audit events are append-only and cannot be updated or deleted");
	}

	@Test
	void persistedAuditEventCannotBeDeleted() {
		AuditEvent event = this.auditService.recordEvent(1L, "TEST_ACTION", "TestTarget", "1", "SUCCESS",
				UUID.randomUUID(), null, null);
		this.entityManager.flush();

		assertThatThrownBy(() -> {
			this.entityManager.remove(event);
			this.entityManager.flush();
		}).isInstanceOf(IllegalStateException.class)
			.hasMessage("Audit events are append-only and cannot be updated or deleted");
	}

}
