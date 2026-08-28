package org.springframework.samples.petclinic.scheduling.audit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.appointment.Appointment;
import org.springframework.samples.petclinic.scheduling.request.RequestRevisionRepository;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;

@Service
public class SchedulingEventLogQueryService {

	private final AuditRecordRepository records;

	private final RequestRevisionRepository revisions;

	public SchedulingEventLogQueryService(AuditRecordRepository records, RequestRevisionRepository revisions) {
		this.records = records;
		this.revisions = revisions;
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> forRequest(SchedulingRequest request) {
		List<String> correlationIds = this.revisions.findByRequestIdOrderByRevisionNumberAsc(request.getId())
			.stream()
			.map(revision -> revision.getCorrelationId())
			.toList();
		if (correlationIds.isEmpty()) {
			return List.of();
		}
		return this.records.findByCorrelationIdInOrderByOccurredAtAsc(correlationIds);
	}

	@Transactional(readOnly = true)
	public List<AuditRecord> forAppointment(Appointment appointment) {
		Map<Integer, AuditRecord> events = new LinkedHashMap<>();
		if (appointment.getRequest() != null) {
			forRequest(appointment.getRequest()).forEach(event -> events.put(event.getId(), event));
		}
		this.records.findByTargetTypeAndTargetIdOrderByOccurredAtAsc("appointment", appointment.getId().toString())
			.forEach(event -> events.put(event.getId(), event));
		List<AuditRecord> result = new ArrayList<>(events.values());
		result.sort(Comparator.comparing(AuditRecord::getOccurredAt));
		return result;
	}

}
