package org.springframework.samples.petclinic.scheduling.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.VetSchedule;
import org.springframework.stereotype.Repository;

@Repository
public interface VetScheduleRepository extends JpaRepository<VetSchedule, Integer> {

	List<VetSchedule> findByVetId(Integer vetId);

	List<VetSchedule> findByVetIdAndDayOfWeek(Integer vetId, int dayOfWeek);

}
