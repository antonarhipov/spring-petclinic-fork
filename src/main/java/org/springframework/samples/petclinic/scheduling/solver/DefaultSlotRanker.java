/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.samples.petclinic.scheduling.interpretation.Interpretation;
import org.springframework.samples.petclinic.scheduling.request.SchedulingRequest;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;

/**
 * Default slot ranker generating candidate slots according to clinic hours and vet
 * availability.
 */
@Service
public class DefaultSlotRanker implements SlotRanker {

	private final VetRepository vetRepository;

	private final Clock clock;

	public DefaultSlotRanker(VetRepository vetRepository, Clock clock) {
		this.vetRepository = vetRepository;
		this.clock = clock;
	}

	@Override
	public List<RankedSlot> rankSlots(SchedulingRequest request, Interpretation interpretation) {
		List<RankedSlot> results = new ArrayList<>();
		int duration = interpretation != null && interpretation.getEstimatedMinutes() != null
				? interpretation.getEstimatedMinutes() : 30;

		ZonedDateTime now = ZonedDateTime.now(this.clock);
		LocalDate startDate = now.toLocalDate().plusDays(1);

		Collection<Vet> vets = this.vetRepository.findAll();
		for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
			LocalDate date = startDate.plusDays(dayOffset);
			if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
				continue;
			}

			LocalTime startT = date.getDayOfWeek() == DayOfWeek.SATURDAY ? LocalTime.of(9, 0) : LocalTime.of(8, 30);
			LocalTime endT = date.getDayOfWeek() == DayOfWeek.SATURDAY ? LocalTime.of(13, 0) : LocalTime.of(17, 30);

			for (Vet vet : vets) {
				if (interpretation != null && interpretation.getSpecialty() != null
						&& !interpretation.getSpecialty().isBlank()) {
					boolean hasSpecialty = vet.getSpecialties()
						.stream()
						.anyMatch(s -> s.getName().equalsIgnoreCase(interpretation.getSpecialty().trim()));
					if (!hasSpecialty) {
						continue;
					}
				}

				LocalTime currentSlot = startT;
				while (!currentSlot.plusMinutes(duration).isAfter(endT)) {
					ZonedDateTime slotStart = ZonedDateTime.of(date, currentSlot, now.getZone());
					results.add(new RankedSlot(vet, slotStart, duration, "Available opening", "0hard/1medium/1soft"));
					currentSlot = currentSlot.plusMinutes(duration);
				}
			}
		}

		return results;
	}

}
