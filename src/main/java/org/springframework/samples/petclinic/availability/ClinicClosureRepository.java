package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicClosureRepository extends JpaRepository<ClinicClosure, Long> {

	@Query("SELECT c FROM ClinicClosure c WHERE c.startDate <= :date AND c.endDate >= :date")
	List<ClinicClosure> findActiveClosuresOnDate(@Param("date") LocalDate date);

	@Query("SELECT c FROM ClinicClosure c WHERE c.startDate <= :endDate AND c.endDate >= :startDate")
	List<ClinicClosure> findClosuresOverlapping(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

}
