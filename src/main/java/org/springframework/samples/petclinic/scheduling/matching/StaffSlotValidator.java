package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.scheduling.config.AvailabilityService;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettings;
import org.springframework.samples.petclinic.scheduling.config.ClinicSettingsRepository;
import org.springframework.stereotype.Component;

@Component
public class StaffSlotValidator {

	private final ClinicSettingsRepository settingsRepository;

	private final AvailabilityService availabilityService;

	public StaffSlotValidator(ClinicSettingsRepository settingsRepository, AvailabilityService availabilityService) {
		this.settingsRepository = settingsRepository;
		this.availabilityService = availabilityService;
	}

	public String refusalFor(int veterinarianId, LocalDate date, LocalTime startTime, int durationMinutes) {
		ClinicSettings settings = this.settingsRepository.getCurrentSettings();
		LocalTime endTime = startTime.plusMinutes(durationMinutes);
		if (durationMinutes < settings.getMinimumDurationMinutes()
				|| durationMinutes > settings.getMaximumDurationMinutes() || !endTime.isAfter(startTime)) {
			return "scheduling.slot.invalid.duration";
		}
		if (!FeasibilityChecker.isGridAligned(startTime)) {
			return "scheduling.slot.invalid.grid";
		}
		boolean open = this.availabilityService.getOpeningHours(date)
			.filter(opening -> FeasibilityChecker.isInsideOpeningHours(startTime, endTime,
					new FeasibilityChecker.OpeningHours(opening.getWeekday(), opening.getOpenTime(),
							opening.getCloseTime())))
			.isPresent();
		if (!open) {
			return "scheduling.slot.invalid.opening";
		}
		return FeasibilityChecker.isInsideEffectiveBlock(veterinarianId, date, startTime, endTime,
				this.availabilityService.getEffectiveAvailability(veterinarianId, date)) ? null
						: "scheduling.slot.invalid.block";
	}

}
