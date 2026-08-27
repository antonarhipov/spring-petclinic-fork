package org.springframework.samples.petclinic.scheduling.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.RequestAvailabilityWindow;

public interface RequestAvailabilityWindowRepository extends JpaRepository<RequestAvailabilityWindow, Integer> {

	List<RequestAvailabilityWindow> findByRequestIdOrderByWindowOrderAscIdAsc(Integer requestId);

	void deleteByRequestId(Integer requestId);

}
