package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EmergencyAuditMapper {

	private final AuditService audit;

	private final ObjectMapper mapper = new ObjectMapper();

	public EmergencyAuditMapper(AuditService audit) {
		this.audit = audit;
	}

	public void record(Long requestId, EmergencyScreeningService.ScreenResult result) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("version", result.version());
		payload.put("matched", result.matched());
		payload.put("terms", result.matchedTerms());
		String json;
		try {
			json = this.mapper.writeValueAsString(payload);
		}
		catch (Exception ex) {
			json = "{}";
		}
		this.audit.record("SYSTEM", null, "EMERGENCY_SCREEN", "SchedulingRequest", String.valueOf(requestId), requestId,
				null, json);
	}

}
