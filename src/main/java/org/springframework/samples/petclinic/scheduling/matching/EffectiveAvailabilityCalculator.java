package org.springframework.samples.petclinic.scheduling.matching;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.samples.petclinic.scheduling.config.ClinicConfiguration.OpeningPeriod;
import org.springframework.samples.petclinic.scheduling.config.ClinicConfiguration.WorkingPeriod;

public final class EffectiveAvailabilityCalculator {

	public List<EffectiveAvailability> calculate(LocalDate date, OpeningPeriod openingHours,
			List<WorkingPeriod> workingPeriods, Set<Integer> unavailableVeterinarianIds, boolean clinicClosed,
			Integer veterinarianId) {
		if (clinicClosed || openingHours == null || !openingHours.isOpen()) {
			return List.of();
		}
		LocalTime clinicOpen = openingHours.openTime();
		LocalTime clinicClose = openingHours.closeTime();
		return workingPeriods.stream()
			.filter(period -> veterinarianId == null || period.veterinarianId() == veterinarianId)
			.filter(period -> !unavailableVeterinarianIds.contains(period.veterinarianId()))
			.map(period -> intersect(date, period, clinicOpen, clinicClose))
			.filter(java.util.Objects::nonNull)
			.sorted(Comparator.comparingInt(EffectiveAvailability::vetId)
				.thenComparing(EffectiveAvailability::startTime))
			.toList();
	}

	private EffectiveAvailability intersect(LocalDate date, WorkingPeriod period, LocalTime clinicOpen,
			LocalTime clinicClose) {
		LocalTime start = period.startTime().isBefore(clinicOpen) ? clinicOpen : period.startTime();
		LocalTime end = period.endTime().isAfter(clinicClose) ? clinicClose : period.endTime();
		return end.isAfter(start) ? new EffectiveAvailability(period.veterinarianId(), date, start, end) : null;
	}

}
