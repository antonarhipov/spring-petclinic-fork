package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VeterinarianLeaveRepository extends JpaRepository<VeterinarianLeave, Long> {

	@Query("SELECT l FROM VeterinarianLeave l WHERE l.vetId = :vetId AND l.startDate <= :date AND l.endDate >= :date")
	List<VeterinarianLeave> findActiveLeaveForVetOnDate(@Param("vetId") Integer vetId, @Param("date") LocalDate date);

	@Query("SELECT l FROM VeterinarianLeave l WHERE l.vetId = :vetId AND l.startDate <= :endDate AND l.endDate >= :startDate")
	List<VeterinarianLeave> findLeaveForVetOverlapping(@Param("vetId") Integer vetId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	@Query("SELECT l FROM VeterinarianLeave l WHERE l.startDate <= :endDate AND l.endDate >= :startDate")
	List<VeterinarianLeave> findAllLeaveOverlapping(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	List<VeterinarianLeave> findByVetId(Integer vetId);

}
