package org.springframework.samples.petclinic.scheduling.config;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClinicZoneClockTests {

	@Test
	void uc7G5SavedClinicZoneDeterminesTodayWithoutChangingTheInstant() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		Instant instant = Instant.parse("2026-09-08T23:30:00Z");
		ClinicZoneClock clock = new ClinicZoneClock(Clock.fixed(instant, ZoneId.of("UTC")), jdbc);
		when(jdbc.queryForObject("select time_zone from clinic_settings", String.class)).thenReturn("America/New_York",
				"Pacific/Auckland");

		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 9, 8));
		assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 9, 9));
		assertThat(clock.instant()).isEqualTo(instant);
	}

}
