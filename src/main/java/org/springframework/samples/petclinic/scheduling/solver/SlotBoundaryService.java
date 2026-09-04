/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHour;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDay;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDayRepository;
import org.springframework.stereotype.Service;

/** Resolves migrated scheduling boundaries against the clinic clock. */
@Service
public class SlotBoundaryService {

	private static final Duration LEAD_TIME = Duration.ofHours(2);

	private final ClinicOpeningHourRepository openingHourRepository;

	private final ClinicPartOfDayRepository partOfDayRepository;

	private final ClinicConfigRepository configRepository;

	private final Clock clock;

	public SlotBoundaryService(ClinicOpeningHourRepository openingHourRepository,
			ClinicPartOfDayRepository partOfDayRepository, ClinicConfigRepository configRepository, Clock clock) {
		this.openingHourRepository = openingHourRepository;
		this.partOfDayRepository = partOfDayRepository;
		this.configRepository = configRepository;
		this.clock = clock;
	}

	/**
	 * Intersects a part-of-day token with the named weekday's actual opening interval.
	 */
	public Optional<TimeRange> resolvePartOfDay(String token, DayOfWeek weekday) {
		ClinicOpeningHour opening = this.openingHourRepository.findAll()
			.stream()
			.filter(candidate -> candidate.getDayOfWeek() == weekday)
			.findFirst()
			.orElseThrow();
		if (opening.isClosed()) {
			return Optional.empty();
		}
		ClinicPartOfDay part = this.partOfDayRepository.findAll()
			.stream()
			.filter(candidate -> candidate.getName().equalsIgnoreCase(token.toLowerCase(Locale.ROOT)))
			.findFirst()
			.orElseThrow();
		LocalTime start = later(opening.getOpenTime(), part.getStartTime());
		LocalTime end = earlier(opening.getCloseTime(), part.getEndTime());
		return start.isBefore(end) ? Optional.of(new TimeRange(start, end)) : Optional.empty();
	}

	/**
	 * First configured grid point that is at least two hours after the injected clock.
	 */
	public ZonedDateTime earliestBookableSlot() {
		ClinicConfig config = this.configRepository.findById(1).orElseThrow();
		ZonedDateTime threshold = ZonedDateTime.now(this.clock).plus(LEAD_TIME).truncatedTo(ChronoUnit.MINUTES);
		int gridMinutes = config.getGridIntervalMinutes();
		int minutesOfDay = threshold.getHour() * 60 + threshold.getMinute();
		int minutesToNextGrid = Math.floorMod(-minutesOfDay, gridMinutes);
		return threshold.plusMinutes(minutesToNextGrid);
	}

	public boolean isBookableByLeadTime(ZonedDateTime candidate) {
		return !candidate.isBefore(earliestBookableSlot());
	}

	private static LocalTime later(LocalTime left, LocalTime right) {
		return left.isAfter(right) ? left : right;
	}

	private static LocalTime earlier(LocalTime left, LocalTime right) {
		return left.isBefore(right) ? left : right;
	}

	public record TimeRange(LocalTime start, LocalTime end) {
	}

}
