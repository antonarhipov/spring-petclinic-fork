package org.springframework.samples.petclinic.owner;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Integer> {

	Optional<Visit> findByAppointmentId(Long appointmentId);

}
