package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicHoursRepository extends JpaRepository<ClinicHours, Long> {

	List<ClinicHours> findByPolicyId(Long policyId);

}
