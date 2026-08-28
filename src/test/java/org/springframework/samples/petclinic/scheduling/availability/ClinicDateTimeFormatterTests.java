package org.springframework.samples.petclinic.scheduling.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class ClinicDateTimeFormatterTests {

	private static final Instant APPOINTMENT_TIME = Instant.parse("2026-08-28T17:41:45.066677Z");

	private static final ZoneId CLINIC_ZONE = ZoneId.of("Europe/Amsterdam");

	@Test
	void formatsTheWeekdayDateAndLocalTimeWithoutIsoNoise() {
		assertThat(ClinicDateTimeFormatter.format(APPOINTMENT_TIME, CLINIC_ZONE, Locale.ENGLISH))
			.isEqualTo("Friday, August 28, 2026 · 7:41\u202fPM CEST");
	}

	@Test
	void providesSeparateDateAndTimeValuesForTheOfferCard() {
		assertThat(ClinicDateTimeFormatter.formatDate(APPOINTMENT_TIME, CLINIC_ZONE, Locale.ENGLISH))
			.isEqualTo("Friday, August 28, 2026");
		assertThat(ClinicDateTimeFormatter.formatTime(APPOINTMENT_TIME, CLINIC_ZONE, Locale.ENGLISH))
			.isEqualTo("7:41\u202fPM CEST");
	}

}
