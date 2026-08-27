package org.springframework.samples.petclinic.scheduling.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

@Service
public class SchedulingTimeService {

	private final Clock clock;

	private final ZoneId clinicZoneId;

	public SchedulingTimeService(Clock clock, ZoneId clinicZoneId) {
		this.clock = clock;
		this.clinicZoneId = clinicZoneId;
	}

	public Clock getClock() {
		return clock;
	}

	public ZoneId getClinicZoneId() {
		return clinicZoneId;
	}

	public Instant now() {
		return Instant.now(clock);
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(clinicZoneId));
	}

	public LocalDateTime nowLocal() {
		return LocalDateTime.now(clock.withZone(clinicZoneId));
	}

	public Instant nextGridBoundary(Instant instant) {
		ZonedDateTime zdt = instant.atZone(clinicZoneId);
		if (zdt.getMinute() % 15 == 0 && zdt.getSecond() == 0 && zdt.getNano() == 0) {
			return instant;
		}
		int nextMinute = ((zdt.getMinute() / 15) + 1) * 15;
		return zdt.truncatedTo(ChronoUnit.HOURS).plusMinutes(nextMinute).toInstant();
	}

	public Instant calculateOwnerHorizonEnd(Instant instant, int horizonDays) {
		LocalDate clinicDate = instant.atZone(clinicZoneId).toLocalDate();
		return clinicDate.plusDays(horizonDays).atStartOfDay(clinicZoneId).toInstant();
	}

	public Instant calculateStaffHorizonEnd(Instant instant, int horizonDays) {
		LocalDate clinicDate = instant.atZone(clinicZoneId).toLocalDate();
		return clinicDate.plusDays(horizonDays).atStartOfDay(clinicZoneId).toInstant();
	}

	public boolean isValidOffset(LocalDateTime localDateTime) {
		return clinicZoneId.getRules().getValidOffsets(localDateTime).size() == 1;
	}

	public boolean isValidDstOffset(Instant instant) {
		if (instant == null) {
			return false;
		}
		LocalDateTime ldt = instant.atZone(clinicZoneId).toLocalDateTime();
		return isValidOffset(ldt);
	}

	public Instant quantizeToNext15MinuteBoundary(Instant instant) {
		return nextGridBoundary(instant);
	}

	public boolean isGridAligned(LocalTime time) {
		return time != null && time.getMinute() % 15 == 0 && time.getSecond() == 0 && time.getNano() == 0;
	}

}
