package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicClosureRepository extends JpaRepository<ClinicClosure, Long> {

	List<ClinicClosure> findByPolicyId(Long policyId);

}
