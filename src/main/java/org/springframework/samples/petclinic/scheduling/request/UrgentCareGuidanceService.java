package org.springframework.samples.petclinic.scheduling.request;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingSettingsRepository;

@Service
public class UrgentCareGuidanceService {

	private final ClinicSchedulingSettingsRepository settings;

	public UrgentCareGuidanceService(ClinicSchedulingSettingsRepository settings) {
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public String guidance() {
		return this.settings.findById(1)
			.map(setting -> setting.getUrgentCareGuidance())
			.orElse("If your pet needs urgent care, contact the clinic or an emergency veterinarian immediately.");
	}

}
