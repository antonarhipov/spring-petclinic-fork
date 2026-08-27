package org.springframework.samples.petclinic.scheduling.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.samples.petclinic.scheduling.model.AuditEventType;
import org.springframework.samples.petclinic.scheduling.model.SchedulingRequest;
import org.springframework.samples.petclinic.scheduling.repository.SchedulingRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetentionService {

	private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

	private final SchedulingRequestRepository schedulingRequestRepository;

	private final LockCoordinator lockCoordinator;

	private final AuditService auditService;

	private final SchedulingTimeService timeService;

	public RetentionService(SchedulingRequestRepository schedulingRequestRepository, LockCoordinator lockCoordinator,
			AuditService auditService, SchedulingTimeService timeService) {
		this.schedulingRequestRepository = schedulingRequestRepository;
		this.lockCoordinator = lockCoordinator;
		this.auditService = auditService;
		this.timeService = timeService;
	}

	public int purgeOverdueRequests() {
		Instant now = timeService.now();
		List<Integer> requestIds = schedulingRequestRepository.findOverdueRetentionRequestIds(now,
				PageRequest.of(0, 50));
		int purgedCount = 0;
		for (Integer id : requestIds) {
			try {
				purgedCount += purgeOverdueRequest(id);
			}
			catch (Exception e) {
				log.error("Failed to purge overdue retention request ID: {}", id, e);
			}
		}
		return purgedCount;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int purgeOverdueRequest(Integer requestId) {
		SchedulingRequest initial = schedulingRequestRepository.findById(requestId).orElse(null);
		if (initial == null || initial.getPurgedAt() != null || initial.getRetentionDeadline() == null) {
			return 0;
		}

		Instant now = timeService.now();
		if (initial.getRetentionDeadline().isAfter(now)) {
			return 0;
		}

		Integer ownerId = initial.getOwner() != null ? initial.getOwner().getId() : null;
		Integer petId = initial.getPet() != null ? initial.getPet().getId() : null;
		Integer vetId = initial.getPreferredVet() != null ? initial.getPreferredVet().getId() : null;

		lockCoordinator.lockResources(ownerId != null ? List.of(ownerId) : null, petId != null ? List.of(petId) : null,
				vetId != null ? List.of(vetId) : null, List.of(requestId), null, null);

		SchedulingRequest locked = schedulingRequestRepository.findById(requestId).orElse(null);
		if (locked == null || locked.getPurgedAt() != null || locked.getRetentionDeadline() == null
				|| locked.getRetentionDeadline().isAfter(now)) {
			return 0;
		}

		locked.setOriginalText(null);
		locked.setAiSummary(null);
		locked.setFullAiResponse(null);
		locked.setPurgedAt(now);
		schedulingRequestRepository.save(locked);

		auditService.recordEvent(AuditEventType.DATA_PURGED, "SYSTEM", "SYSTEM", "SCHEDULING_REQUEST",
				String.valueOf(requestId), "RETENTION_EXPIRED",
				Map.of("requestId", String.valueOf(requestId), "purgedAt", now.toString()));

		log.info("Purged sensitive data for request ID: {}", requestId);
		return 1;
	}

}
