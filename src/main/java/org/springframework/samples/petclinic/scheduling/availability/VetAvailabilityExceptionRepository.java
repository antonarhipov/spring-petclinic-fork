package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VetAvailabilityExceptionRepository extends JpaRepository<VetAvailabilityException, Integer> {

	List<VetAvailabilityException> findByVetId(Integer vetId);

}
