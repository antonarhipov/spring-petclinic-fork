package org.springframework.samples.petclinic.scheduling.appointment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

	List<Appointment> findByRequestId(Long requestId);

	List<Appointment> findByPetIdInOrderByStartAtDesc(List<Integer> petIds);

	List<Appointment> findByStatus(AppointmentStatus status);

}
