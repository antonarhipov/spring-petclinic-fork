package org.springframework.samples.petclinic.scheduling.availability;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringVetShiftRepository extends JpaRepository<RecurringVetShift, Integer> {

	List<RecurringVetShift> findByVetIdAndDayOfWeek(Integer vetId, int dayOfWeek);

}
