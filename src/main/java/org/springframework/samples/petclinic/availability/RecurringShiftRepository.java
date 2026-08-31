package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecurringShiftRepository extends JpaRepository<RecurringShift, Long> {

	List<RecurringShift> findByVetId(Integer vetId);

	List<RecurringShift> findByVetIdAndWeekday(Integer vetId, DayOfWeek weekday);

	List<RecurringShift> findByWeekday(DayOfWeek weekday);

}
