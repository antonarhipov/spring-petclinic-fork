package org.springframework.samples.petclinic.scheduling.queue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class StaffQueueAuditService {

	private final AuditService audit;

	private final ObjectMapper mapper = new ObjectMapper();

	public StaffQueueAuditService(AuditService audit) {
		this.audit = audit;
	}

	public void record(Long staffAccountId, String action, Long requestId, String afterJson) {
		this.audit.record("STAFF", staffAccountId, action, "StaffQueueItem", String.valueOf(requestId), requestId, null,
				afterJson);
	}

	public String json(Object... keysAndValues) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
			values.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
		}
		try {
			return this.mapper.writeValueAsString(values);
		}
		catch (Exception ex) {
			return "{}";
		}
	}

}
