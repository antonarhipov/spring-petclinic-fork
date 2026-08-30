package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VetRecurringShiftRepository extends JpaRepository<VetRecurringShift, Long> {

	List<VetRecurringShift> findByVeterinarianId(Integer veterinarianId);

}
