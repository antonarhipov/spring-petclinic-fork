package org.springframework.samples.petclinic.scheduling.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.samples.petclinic.scheduling.model.ClinicClosure;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicClosureRepository extends JpaRepository<ClinicClosure, Integer> {

	List<ClinicClosure> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);

}
