package org.springframework.samples.petclinic.scheduling.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.VetScheduleException;
import org.springframework.stereotype.Repository;

@Repository
public interface VetScheduleExceptionRepository extends JpaRepository<VetScheduleException, Integer> {

	List<VetScheduleException> findByVetId(Integer vetId);

	List<VetScheduleException> findByVetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(Integer vetId,
			LocalDate endDate, LocalDate startDate);

}
