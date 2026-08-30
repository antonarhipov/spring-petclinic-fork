package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VetDateExceptionRepository extends JpaRepository<VetDateException, Long> {

	List<VetDateException> findByVeterinarianId(Integer veterinarianId);

	Optional<VetDateException> findByVeterinarianIdAndExceptionDate(Integer veterinarianId, LocalDate exceptionDate);

}
