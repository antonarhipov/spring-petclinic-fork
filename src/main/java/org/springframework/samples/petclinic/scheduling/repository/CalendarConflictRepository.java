package org.springframework.samples.petclinic.scheduling.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.CalendarConflict;
import org.springframework.samples.petclinic.scheduling.model.ConflictStatus;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarConflictRepository extends JpaRepository<CalendarConflict, Integer> {

	List<CalendarConflict> findByAppointmentId(Integer appointmentId);

	List<CalendarConflict> findByAppointmentIdAndStatus(Integer appointmentId, ConflictStatus status);

	List<CalendarConflict> findByVetIdAndStatus(Integer vetId, ConflictStatus status);

	List<CalendarConflict> findByStatus(ConflictStatus status);

}
