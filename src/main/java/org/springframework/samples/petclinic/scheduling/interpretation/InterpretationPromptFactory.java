/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Comparator;

import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfig;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicConfigRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicOpeningHourRepository;
import org.springframework.samples.petclinic.scheduling.clinic.ClinicPartOfDayRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the complete, deterministic structured-output prompt. */
@Service
public class InterpretationPromptFactory {

	private final Clock clock;

	private final ClinicConfigRepository configRepository;

	private final ClinicOpeningHourRepository openingHourRepository;

	private final ClinicPartOfDayRepository partOfDayRepository;

	private final VetRepository vetRepository;

	public InterpretationPromptFactory(Clock clock, ClinicConfigRepository configRepository,
			ClinicOpeningHourRepository openingHourRepository, ClinicPartOfDayRepository partOfDayRepository,
			VetRepository vetRepository) {
		this.clock = clock;
		this.configRepository = configRepository;
		this.openingHourRepository = openingHourRepository;
		this.partOfDayRepository = partOfDayRepository;
		this.vetRepository = vetRepository;
	}

	@Transactional(readOnly = true)
	public String create(String reasonText, String availabilityText) {
		ClinicConfig config = this.configRepository.findById(1).orElseThrow();
		ZonedDateTime now = ZonedDateTime.now(this.clock);
		ZonedDateTime horizonEnd = now.toLocalDate()
			.plusDays(config.getBookingHorizonDays())
			.atTime(LocalTime.MAX)
			.atZone(this.clock.getZone());
		StringBuilder prompt = new StringBuilder();
		prompt.append("currentDateTime=").append(now).append('\n');
		prompt.append("currentDayOfWeek=").append(now.getDayOfWeek()).append('\n');
		prompt.append("horizonEnd=").append(horizonEnd).append('\n');
		prompt.append("openingHours:\n");
		this.openingHourRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(hour -> hour.getDayOfWeek().getValue()))
			.forEach(hour -> prompt.append(hour.getDayOfWeek())
				.append('=')
				.append(hour.isClosed() ? "CLOSED" : hour.getOpenTime() + "-" + hour.getCloseTime())
				.append('\n'));
		prompt.append("partOfDayTokens:\n");
		this.partOfDayRepository.findAll()
			.stream()
			.sorted(Comparator.comparing(value -> value.getName().toUpperCase()))
			.forEach(value -> prompt.append(value.getName().toUpperCase())
				.append('=')
				.append(value.getStartTime())
				.append('-')
				.append(value.getEndTime())
				.append('\n'));
		prompt.append("veterinarians:\n");
		this.vetRepository.findAll()
			.stream()
			.sorted(Comparator.comparingInt(vet -> vet.getId()))
			.forEach(vet -> prompt.append(vet.getId())
				.append('=')
				.append(vet.getFirstName())
				.append(' ')
				.append(vet.getLastName())
				.append(" specialties=")
				.append(vet.getSpecialties().stream().map(specialty -> specialty.getName()).sorted().toList())
				.append('\n'));
		prompt.append("offeredSpecialties=[radiology, surgery, dentistry]\n");
		prompt.append("reason=").append(reasonText).append('\n');
		prompt.append("availability=").append(availabilityText);
		return prompt.toString();
	}

}
