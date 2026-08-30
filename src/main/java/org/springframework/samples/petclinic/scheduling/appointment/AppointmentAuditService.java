package org.springframework.samples.petclinic.scheduling.appointment;

import org.springframework.samples.petclinic.scheduling.audit.AuditService;
import org.springframework.stereotype.Service;

@Service
public class AppointmentAuditService {

	private final AuditService audit;

	public AppointmentAuditService(AuditService audit) {
		this.audit = audit;
	}

	public void record(String actorType, Long actorAccountId, String action, Appointment appointment, String beforeJson,
			String afterJson) {
		this.audit.record(actorType, actorAccountId, action, "APPOINTMENT", String.valueOf(appointment.getId()),
				appointment.getRequestId(), beforeJson, afterJson);
	}

}
