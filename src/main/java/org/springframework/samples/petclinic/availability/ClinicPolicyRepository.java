package org.springframework.samples.petclinic.availability;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicPolicyRepository extends JpaRepository<ClinicPolicy, Integer> {

	default Optional<ClinicPolicy> findSingleton() {
		return findById(1);
	}

}
