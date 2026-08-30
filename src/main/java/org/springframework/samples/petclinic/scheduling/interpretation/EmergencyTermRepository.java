package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyTermRepository extends JpaRepository<EmergencyTerm, Long> {

	List<EmergencyTerm> findByPolicyId(Long policyId);

}
