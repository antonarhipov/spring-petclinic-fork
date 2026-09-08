package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.jdbc.core.JdbcTemplate;

final class ClinicZoneClock extends Clock {

	private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Amsterdam");

	private final Clock instantSource;

	private final JdbcTemplate jdbc;

	ClinicZoneClock(Clock instantSource, JdbcTemplate jdbc) {
		this.instantSource = instantSource;
		this.jdbc = jdbc;
	}

	@Override
	public ZoneId getZone() {
		try {
			String zone = this.jdbc.queryForObject("select time_zone from clinic_settings", String.class);
			return zone != null ? ZoneId.of(zone) : DEFAULT_ZONE;
		}
		catch (RuntimeException ex) {
			return DEFAULT_ZONE;
		}
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return this.instantSource.withZone(zone);
	}

	@Override
	public Instant instant() {
		return this.instantSource.instant();
	}

}
