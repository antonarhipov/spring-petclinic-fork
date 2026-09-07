package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VetAvailabilityRepository extends JpaRepository<VetWorkingBlock, Integer> {

	@Query("select b from VetWorkingBlock b where b.vet.id = :vetId and b.weekday = :weekday order by b.startTime")
	List<VetWorkingBlock> findWorkingBlocks(@Param("vetId") int vetId, @Param("weekday") DayOfWeek weekday);

	@Query("select b from VetWorkingBlock b where b.weekday = :weekday order by b.vet.id, b.startTime")
	List<VetWorkingBlock> findWorkingBlocksByWeekday(@Param("weekday") DayOfWeek weekday);

	@Query("select b from VetWorkingBlock b where b.vet.id = :vetId order by b.weekday, b.startTime")
	List<VetWorkingBlock> findWorkingBlocksByVet(@Param("vetId") int vetId);

	@Query("select count(e) > 0 from VetException e where e.vet.id = :vetId and e.exceptionDate = :date")
	boolean hasException(@Param("vetId") int vetId, @Param("date") LocalDate date);

	@Query("select e.vet.id from VetException e where e.exceptionDate = :date")
	List<Integer> findExceptionVetIds(@Param("date") LocalDate date);

	@Query("select count(l) > 0 from VetLeave l where l.vet.id = :vetId and l.startDate <= :date and l.endDate >= :date")
	boolean isOnLeave(@Param("vetId") int vetId, @Param("date") LocalDate date);

	@Query("select l.vet.id from VetLeave l where l.startDate <= :date and l.endDate >= :date")
	List<Integer> findLeaveVetIds(@Param("date") LocalDate date);

	@Query("select count(c) > 0 from ClinicClosure c where c.closureDate = :date")
	boolean isClinicClosed(@Param("date") LocalDate date);

}
