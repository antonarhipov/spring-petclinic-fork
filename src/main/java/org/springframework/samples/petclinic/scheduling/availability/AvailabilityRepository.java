package org.springframework.samples.petclinic.scheduling.availability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityRepository extends JpaRepository<ClinicSchedulingPolicy, Long> {

	default ClinicSchedulingPolicy currentPolicy() {
		return findAll().stream().findFirst().orElseThrow();
	}

}
