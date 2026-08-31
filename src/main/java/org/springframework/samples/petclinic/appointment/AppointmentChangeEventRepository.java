package org.springframework.samples.petclinic.appointment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentChangeEventRepository extends JpaRepository<AppointmentChangeEvent, Long> {

	List<AppointmentChangeEvent> findByAppointmentIdOrderByOccurredAtAsc(Long appointmentId);

}
