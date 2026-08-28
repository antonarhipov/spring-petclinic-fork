package org.springframework.samples.petclinic.scheduling.availability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicClosureRepository extends JpaRepository<ClinicClosure, Integer> {

}
