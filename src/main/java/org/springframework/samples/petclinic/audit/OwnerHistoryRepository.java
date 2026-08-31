package org.springframework.samples.petclinic.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface OwnerHistoryRepository extends Repository<OwnerHistoryEvent, Long> {

	OwnerHistoryEvent save(OwnerHistoryEvent ownerHistoryEvent);

	List<OwnerHistoryEvent> findByOwnerIdOrderByOccurredAtDesc(Integer ownerId);

}
