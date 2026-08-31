package org.springframework.samples.petclinic.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OwnerHistoryQueryService {

	private final OwnerHistoryRepository ownerHistoryRepository;

	public OwnerHistoryQueryService(OwnerHistoryRepository ownerHistoryRepository) {
		this.ownerHistoryRepository = Objects.requireNonNull(ownerHistoryRepository,
				"ownerHistoryRepository must not be null");
	}

	public record OwnerHistoryItemDto(Long id, Instant occurredAt, String displayType, String summary,
			String targetType, String targetId) {
	}

	public List<OwnerHistoryItemDto> getHistoryForOwner(Integer ownerId) {
		Objects.requireNonNull(ownerId, "ownerId must not be null");
		List<OwnerHistoryEvent> events = this.ownerHistoryRepository.findByOwnerIdOrderByOccurredAtDesc(ownerId);
		return events.stream()
			.map(e -> new OwnerHistoryItemDto(e.getId(), e.getOccurredAt(), e.getDisplayType(), e.getSummary(),
					e.getTargetType(), e.getTargetId()))
			.toList();
	}

}
