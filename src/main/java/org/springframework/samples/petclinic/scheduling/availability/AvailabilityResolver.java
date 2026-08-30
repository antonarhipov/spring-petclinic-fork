package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.samples.petclinic.scheduling.matching.HoursFact;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityResolver {

	public boolean clinicOpen(List<HoursFact> clinicHours, ZoneId zone, Instant startAt, Instant endAt) {
		return covered(clinicHours.stream().filter(h -> h.veterinarianId() == null).toList(), zone, startAt, endAt);
	}

	public boolean veterinarianWorking(List<HoursFact> veterinarianHours, List<HoursFact> clinicHours, ZoneId zone,
			int veterinarianId, Instant startAt, Instant endAt) {
		List<HoursFact> hours = veterinarianHours.stream()
			.filter(h -> h.veterinarianId() != null && h.veterinarianId() == veterinarianId)
			.toList();
		if (hours.isEmpty()) {
			return clinicOpen(clinicHours, zone, startAt, endAt);
		}
		return covered(hours, zone, startAt, endAt);
	}

	private boolean covered(List<HoursFact> hours, ZoneId zone, Instant startAt, Instant endAt) {
		Instant cursor = startAt;
		while (cursor.isBefore(endAt)) {
			LocalDateTime local = LocalDateTime.ofInstant(cursor, zone);
			boolean open = hours.stream()
				.anyMatch(h -> h.dayOfWeek() == local.getDayOfWeek() && !local.toLocalTime().isBefore(h.startLocal())
						&& local.toLocalTime().isBefore(h.endLocal()));
			if (!open) {
				return false;
			}
			cursor = cursor.plusSeconds(15 * 60);
		}
		return true;
	}

}
