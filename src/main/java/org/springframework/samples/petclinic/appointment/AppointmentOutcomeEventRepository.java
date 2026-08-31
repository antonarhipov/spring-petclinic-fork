package org.springframework.samples.petclinic.appointment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentOutcomeEventRepository extends JpaRepository<AppointmentOutcomeEvent, Long> {

	List<AppointmentOutcomeEvent> findByAppointmentIdOrderByOccurredAtAsc(Long appointmentId);

}
