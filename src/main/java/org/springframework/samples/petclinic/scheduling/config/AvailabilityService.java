package org.springframework.samples.petclinic.scheduling.config;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailability;
import org.springframework.samples.petclinic.scheduling.matching.EffectiveAvailabilityCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityService {

	private final ClinicSettingsRepository settingsRepository;

	private final VetAvailabilityRepository availabilityRepository;

	private final EffectiveAvailabilityCalculator calculator;

	public AvailabilityService(ClinicSettingsRepository settingsRepository,
			VetAvailabilityRepository availabilityRepository, EffectiveAvailabilityCalculator calculator) {
		this.settingsRepository = settingsRepository;
		this.availabilityRepository = availabilityRepository;
		this.calculator = calculator;
	}

	public boolean isClinicClosed(LocalDate date) {
		return this.availabilityRepository.isClinicClosed(date);
	}

	public Optional<OpeningHours> getOpeningHours(LocalDate date) {
		if (this.availabilityRepository.isClinicClosed(date)) {
			return Optional.empty();
		}
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
		Optional<OpeningHours> openingHoursOpt = getOpeningHours(date);
		List<VetWorkingBlock> blocks = (vetIdFilter != null)
				? this.availabilityRepository.findWorkingBlocks(vetIdFilter, date.getDayOfWeek())
				: this.availabilityRepository.findWorkingBlocksByWeekday(date.getDayOfWeek());
		Set<Integer> unavailableVets = new HashSet<>(this.availabilityRepository.findExceptionVetIds(date));
		unavailableVets.addAll(this.availabilityRepository.findLeaveVetIds(date));
		ClinicConfiguration.OpeningPeriod opening = openingHoursOpt
			.map(value -> new ClinicConfiguration.OpeningPeriod(value.getWeekday(), value.getOpenTime(),
					value.getCloseTime()))
			.orElse(null);
		List<ClinicConfiguration.WorkingPeriod> periods = blocks.stream()
			.map(block -> new ClinicConfiguration.WorkingPeriod(block.getVet().getId(), block.getWeekday(),
					block.getStartTime(), block.getEndTime()))
			.toList();
		return this.calculator.calculate(date, opening, periods, unavailableVets,
				this.availabilityRepository.isClinicClosed(date), vetIdFilter);
	}

}
