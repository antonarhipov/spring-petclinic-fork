package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VetLeaveRepository extends JpaRepository<VetLeave, Integer> {

	List<VetLeave> findByVetId(Integer vetId);

}
