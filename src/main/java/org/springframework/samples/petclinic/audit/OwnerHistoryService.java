package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerHistoryService {

	private final OwnerHistoryRepository ownerHistoryRepository;

	public OwnerHistoryService(OwnerHistoryRepository ownerHistoryRepository) {
		this.ownerHistoryRepository = Objects.requireNonNull(ownerHistoryRepository,
				"ownerHistoryRepository must not be null");
	}

	public OwnerHistoryEvent recordEvent(Integer ownerId, String displayType, String summary, String targetType,
			String targetId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(displayType, "displayType must not be null");
		Objects.requireNonNull(summary, "summary must not be null");
		Objects.requireNonNull(targetType, "targetType must not be null");
		Objects.requireNonNull(targetId, "targetId must not be null");

		OwnerHistoryEvent event = new OwnerHistoryEvent();
		event.setOwnerId(ownerId);
		event.setDisplayType(displayType);
		event.setSummary(summary);
		event.setTargetType(targetType);
		event.setTargetId(targetId);
		event.setOccurredAt(Instant.now());

		return this.ownerHistoryRepository.save(event);
	}

	public OwnerHistoryEvent recordOwnerHistory(Integer ownerId, Integer petId, Long targetId, String displayType,
			String summary, String additionalDetails) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		Objects.requireNonNull(displayType, "displayType must not be null");
		Objects.requireNonNull(summary, "summary must not be null");

		OwnerHistoryEvent event = new OwnerHistoryEvent();
		event.setOwnerId(ownerId);
		event.setDisplayType(displayType);
		event.setSummary(summary);
		event.setTargetType("REQUEST");
		event.setTargetId(targetId != null ? targetId.toString() : (petId != null ? petId.toString() : "0"));
		event.setOccurredAt(Instant.now());
		return this.ownerHistoryRepository.save(event);
	}

	@Transactional(readOnly = true)
	public List<OwnerHistoryEvent> findHistoryForOwner(Integer ownerId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		return this.ownerHistoryRepository.findByOwnerIdOrderByOccurredAtDesc(ownerId);
	}

}
