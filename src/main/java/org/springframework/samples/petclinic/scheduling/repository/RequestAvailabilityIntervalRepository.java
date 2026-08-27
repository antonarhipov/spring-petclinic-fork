package org.springframework.samples.petclinic.scheduling.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityInterval;

public interface RequestAvailabilityIntervalRepository extends JpaRepository<RequestAvailabilityInterval, Integer> {

	List<RequestAvailabilityInterval> findByRequestIdOrderByStartInstantAscIdAsc(Integer requestId);

	void deleteByRequestId(Integer requestId);

}
