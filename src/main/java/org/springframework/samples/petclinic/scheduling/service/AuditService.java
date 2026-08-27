package org.springframework.samples.petclinic.scheduling.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.AuditRecord;
import org.springframework.samples.petclinic.scheduling.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);

	// Sensitive keys that MUST NEVER be serialized into audit metadata per RULE-10 &
	// UC7-RULE7
	private static final Set<String> DISALLOWED_KEYS = Set.of("password", "passwordhash", "rawtext", "originaltext",
			"text", "notes", "clinicalnotes", "description", "prompt", "response", "token");

	private final AuditRecordRepository auditRecordRepository;

	private final Clock clock;

	public AuditService(AuditRecordRepository auditRecordRepository, Clock clock) {
		this.auditRecordRepository = auditRecordRepository;
		this.clock = clock;
	}

	public AuditRecord recordEvent(AuditEventType eventType, String actorUsername, String actorRole,
			String targetEntityType, String targetEntityId, String reasonCode, Map<String, Object> metadata) {

		AuditRecord record = new AuditRecord();
		record.setEventType(eventType);
		record.setActorUsername(actorUsername != null ? actorUsername : "SYSTEM");
		record.setActorRole(actorRole != null ? actorRole : "SYSTEM");
		record.setTargetEntityType(targetEntityType);
		record.setTargetEntityId(targetEntityId != null ? targetEntityId : "0");
		record.setReasonCode(reasonCode);
		record.setOccurredAt(Instant.now(clock));

		if (metadata != null && !metadata.isEmpty()) {
			record.setMetadataJson(sanitizeAndSerializeMetadata(metadata));
		}

		return auditRecordRepository.save(record);
	}

	String sanitizeAndSerializeMetadata(Map<String, Object> metadata) {
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : metadata.entrySet()) {
			String key = entry.getKey();
			if (key == null || DISALLOWED_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
				continue;
			}
			Object value = entry.getValue();
			if (!(value instanceof String || value instanceof Number || value instanceof Boolean || value == null)) {
				value = String.valueOf(value);
			}
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append('"').append(escapeJson(key)).append('"').append(':');
			appendJsonValue(json, value);
		}
		json.append('}');
		String result = json.toString();
		if ("{}".equals(result) && metadata != null && !metadata.isEmpty()) {
			log.debug("All audit metadata keys were filtered as sensitive");
		}
		return result;
	}

	private static void appendJsonValue(StringBuilder json, Object value) {
		if (value == null) {
			json.append("null");
		}
		else if (value instanceof Boolean || value instanceof Number) {
			json.append(value);
		}
		else {
			json.append('"').append(escapeJson(String.valueOf(value))).append('"');
		}
	}

	private static String escapeJson(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> escaped.append("\\\"");
				case '\\' -> escaped.append("\\\\");
				case '\b' -> escaped.append("\\b");
				case '\f' -> escaped.append("\\f");
				case '\n' -> escaped.append("\\n");
				case '\r' -> escaped.append("\\r");
				case '\t' -> escaped.append("\\t");
				default -> {
					if (c < 0x20) {
						escaped.append(String.format("\\u%04x", (int) c));
					}
					else {
						escaped.append(c);
					}
				}
			}
		}
		return escaped.toString();
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> findAll(Pageable pageable) {
		return auditRecordRepository.findAllByOrderByOccurredAtDesc(pageable);
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> findAll() {
		return auditRecordRepository.findAllByOrderByOccurredAtDesc();
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> findByTarget(String targetEntityType, String targetEntityId) {
		return auditRecordRepository.findByTargetEntityTypeAndTargetEntityIdOrderByOccurredAtDesc(targetEntityType,
				targetEntityId);
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> findTimelineForRequest(Integer requestId, List<Integer> reservationIds,
			Integer appointmentId) {
		List<String> resIdStrs = (reservationIds != null && !reservationIds.isEmpty())
				? reservationIds.stream().map(String::valueOf).toList() : List.of("0");
		String apptIdStr = appointmentId != null ? String.valueOf(appointmentId) : "0";
		return auditRecordRepository.findTimelineForRequest(String.valueOf(requestId), resIdStrs, apptIdStr);
	}

}
