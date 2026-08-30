package org.springframework.samples.petclinic.scheduling.appointment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationBlockRepository extends JpaRepository<ReservationBlock, ReservationBlockId> {

	List<ReservationBlock> findByHoldId(Long holdId);

	List<ReservationBlock> findByAppointmentId(Long appointmentId);

	List<ReservationBlock> findAll();

}
