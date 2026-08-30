package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AllowedDurationRepository extends JpaRepository<AllowedDuration, Long> {

	List<AllowedDuration> findByPolicyId(Long policyId);

}
