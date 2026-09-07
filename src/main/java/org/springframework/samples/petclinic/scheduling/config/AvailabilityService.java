package org.springframework.samples.petclinic.scheduling.config;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

	private final ClinicSettingsRepository settingsRepository;

	private final VetAvailabilityRepository availabilityRepository;

	public AvailabilityService(ClinicSettingsRepository settingsRepository,
			VetAvailabilityRepository availabilityRepository) {
		this.settingsRepository = settingsRepository;
		this.availabilityRepository = availabilityRepository;
	}

	public boolean isClinicClosed(LocalDate date) {
		return this.availabilityRepository.isClinicClosed(date);
	}

	public Optional<OpeningHours> getOpeningHours(LocalDate date) {
		return this.settingsRepository.findCurrentSettings()
			.flatMap(s -> s.getOpeningHoursFor(date.getDayOfWeek()))
			.filter(OpeningHours::isOpen);
	}

	public List<EffectiveAvailability> getEffectiveAvailability(int vetId, LocalDate date) {
		return computeEffectiveAvailability(date, vetId);
	}

	public List<EffectiveAvailability> getEffectiveAvailability(LocalDate date) {
		return computeEffectiveAvailability(date, null);
	}

	public List<EffectiveAvailability> getEffectiveAvailability(LocalDate startDate, LocalDate endDate) {
		List<EffectiveAvailability> result = new ArrayList<>();
		LocalDate current = startDate;
		while (!current.isAfter(endDate)) {
			result.addAll(computeEffectiveAvailability(current, null));
			current = current.plusDays(1);
		}
		return result;
	}

	public List<EffectiveAvailability> getEffectiveAvailability(int vetId, LocalDate startDate, LocalDate endDate) {
		List<EffectiveAvailability> result = new ArrayList<>();
		LocalDate current = startDate;
		while (!current.isAfter(endDate)) {
			result.addAll(computeEffectiveAvailability(current, vetId));
			current = current.plusDays(1);
		}
		return result;
	}

	private List<EffectiveAvailability> computeEffectiveAvailability(LocalDate date, Integer vetIdFilter) {
		if (this.availabilityRepository.isClinicClosed(date)) {
			return List.of();
		}

		Optional<OpeningHours> openingHoursOpt = getOpeningHours(date);
		if (openingHoursOpt.isEmpty()) {
			return List.of();
		}

		OpeningHours opening = openingHoursOpt.get();
		LocalTime openTime = opening.getOpenTime();
		LocalTime closeTime = opening.getCloseTime();

		List<Integer> exceptionVets = this.availabilityRepository.findExceptionVetIds(date);
		List<Integer> leaveVets = this.availabilityRepository.findLeaveVetIds(date);

		List<VetWorkingBlock> blocks = (vetIdFilter != null)
				? this.availabilityRepository.findWorkingBlocks(vetIdFilter, date.getDayOfWeek())
				: this.availabilityRepository.findWorkingBlocksByWeekday(date.getDayOfWeek());

		List<EffectiveAvailability> result = new ArrayList<>();
		for (VetWorkingBlock block : blocks) {
			int vetId = block.getVet().getId();
			if (exceptionVets.contains(vetId)) {
				continue;
			}
			if (leaveVets.contains(vetId)) {
				continue;
			}

			LocalTime start = block.getStartTime().isBefore(openTime) ? openTime : block.getStartTime();
			LocalTime end = block.getEndTime().isAfter(closeTime) ? closeTime : block.getEndTime();

			if (end.isAfter(start)) {
				result.add(new EffectiveAvailability(vetId, date, start, end));
			}
		}

		result.sort(
				Comparator.comparingInt(EffectiveAvailability::vetId).thenComparing(EffectiveAvailability::startTime));
		return result;
	}

}
