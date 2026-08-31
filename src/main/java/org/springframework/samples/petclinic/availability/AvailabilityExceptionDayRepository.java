package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityExceptionDayRepository extends JpaRepository<AvailabilityExceptionDay, Long> {

	Optional<AvailabilityExceptionDay> findByVetIdAndLocalDate(Integer vetId, LocalDate localDate);

	List<AvailabilityExceptionDay> findByVetIdAndLocalDateBetween(Integer vetId, LocalDate startDate,
			LocalDate endDate);

	@Query("SELECT e FROM AvailabilityExceptionDay e WHERE e.localDate BETWEEN :startDate AND :endDate")
	List<AvailabilityExceptionDay> findByLocalDateBetween(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	List<AvailabilityExceptionDay> findByVetId(Integer vetId);

}
