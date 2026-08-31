package org.springframework.samples.petclinic.owner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitRepository extends JpaRepository<Visit, Integer> {

	List<Visit> findByPetIdOrderByDateAsc(Integer petId);

	Optional<Visit> findByAppointmentId(Long appointmentId);

	List<Visit> findAllByAppointmentId(Long appointmentId);

	@Query("SELECT v FROM Visit v WHERE v.date >= :fromDate AND v.provenance = 'LEGACY' AND v.appointmentId IS NULL ORDER BY v.date ASC")
	List<Visit> findFutureLegacyVisits(@Param("fromDate") LocalDate fromDate);

}
