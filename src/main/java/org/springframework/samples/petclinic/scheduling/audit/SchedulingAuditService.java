package org.springframework.samples.petclinic.scheduling.audit;

import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulingAuditService {

	private final AuditRecordRepository records;

	private final Clock clock;

	public SchedulingAuditService(AuditRecordRepository records, Clock clock) {
		this.records = records;
		this.clock = clock;
	}

	@Transactional
	public void record(Authentication authentication, String correlationId, AuditAction action, String targetType,
			Object targetId, String priorValue, String resultingValue, String reason) {
		String actor = authentication == null ? "system" : authentication.getName();
		this.records.save(new AuditRecord(this.clock.instant(), correlationId, actor, action, targetType,
				String.valueOf(targetId), redact(priorValue), redact(resultingValue), redact(reason)));
	}

	private String redact(String value) {
		if (value == null) {
			return null;
		}
		return value.replaceAll("(?i)(password|source[_ ]?text|token)\\s*[:=]\\s*[^,;\\s]+", "$1=[redacted]");
	}

}
