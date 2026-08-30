package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NamedPeriodRepository extends JpaRepository<NamedPeriod, Long> {

	List<NamedPeriod> findByPolicyId(Long policyId);

	Optional<NamedPeriod> findByCode(String code);

}
