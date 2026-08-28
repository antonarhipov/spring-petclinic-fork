package org.springframework.samples.petclinic.scheduling.availability;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NamedDayPeriodRepository extends JpaRepository<NamedDayPeriod, Integer> {

}
