package org.springframework.samples.petclinic.availability;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CalendarStateRepository extends JpaRepository<CalendarState, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT c FROM CalendarState c WHERE c.id = :id")
	Optional<CalendarState> findByIdForUpdate(@Param("id") Integer id);

	default Optional<CalendarState> findSingletonForUpdate() {
		return findByIdForUpdate(1);
	}

}
