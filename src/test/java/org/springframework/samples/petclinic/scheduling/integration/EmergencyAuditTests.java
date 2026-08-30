package org.springframework.samples.petclinic.scheduling.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyAuditMapper;
import org.springframework.samples.petclinic.scheduling.interpretation.EmergencyScreeningService;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmergencyAuditTests {

	@Test
	void matchedScreenPersistsValidJson() {
		AuditService audit = mock(AuditService.class);
		EmergencyAuditMapper mapper = new EmergencyAuditMapper(audit);
		mapper.record(9L, new EmergencyScreeningService.ScreenResult("emergency-v1", List.of("bleeding"), true));
		verify(audit).record(eq("SYSTEM"), isNull(), eq("EMERGENCY_SCREEN"), eq("SchedulingRequest"), eq("9"), eq(9L),
				isNull(), contains("\"matched\":true"));
	}

}
