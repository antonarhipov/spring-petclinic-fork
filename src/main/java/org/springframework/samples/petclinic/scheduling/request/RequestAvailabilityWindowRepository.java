package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestAvailabilityWindowRepository extends JpaRepository<RequestAvailabilityWindow, Integer> {

}
