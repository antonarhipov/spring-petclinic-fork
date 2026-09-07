package org.springframework.samples.petclinic.scheduling.config;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClinicSettingsRepository extends JpaRepository<ClinicSettings, Integer> {

	@Query("select s from ClinicSettings s order by s.id asc")
	Optional<ClinicSettings> findCurrentSettings();

	default ClinicSettings getCurrentSettings() {
		return findCurrentSettings().orElseThrow(() -> new IllegalStateException("Clinic settings not found"));
	}

}
