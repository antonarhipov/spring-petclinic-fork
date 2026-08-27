package org.springframework.samples.petclinic.scheduling.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, Integer> {

	default Optional<ClinicSettings> findDefaultSettings() {
		return findById(1);
	}

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM ClinicSettings s WHERE s.id = :id")
	Optional<ClinicSettings> findByIdForUpdate(Integer id);

}
