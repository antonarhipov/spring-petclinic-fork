package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component("clinicDateTime")
@RequestScope
public class ClinicDateTimeFormatter {

	private final ClinicSchedulingSettingsService settings;

	private ZoneId clinicZone;

	public ClinicDateTimeFormatter(ClinicSchedulingSettingsService settings) {
		this.settings = settings;
	}

	public String format(Instant instant) {
		return format(instant, clinicZone(), LocaleContextHolder.getLocale());
	}

	public String formatDate(Instant instant) {
		return formatDate(instant, clinicZone(), LocaleContextHolder.getLocale());
	}

	public String formatTime(Instant instant) {
		return formatTime(instant, clinicZone(), LocaleContextHolder.getLocale());
	}

	public String formatInput(Instant instant) {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").format(LocalDateTime.ofInstant(instant, clinicZone()));
	}

	static String format(Instant instant, ZoneId zone, Locale locale) {
		return formatDate(instant, zone, locale) + " · " + formatTime(instant, zone, locale);
	}

	static String formatDate(Instant instant, ZoneId zone, Locale locale) {
		ZonedDateTime dateTime = instant.atZone(zone);
		return DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(dateTime);
	}

	static String formatTime(Instant instant, ZoneId zone, Locale locale) {
		ZonedDateTime dateTime = instant.atZone(zone);
		String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(dateTime);
		String zoneName = DateTimeFormatter.ofPattern("z", locale).format(dateTime);
		return time + " " + zoneName;
	}

	private ZoneId clinicZone() {
		if (this.clinicZone == null) {
			this.clinicZone = ZoneId.of(this.settings.get().getClinicZone());
		}
		return this.clinicZone;
	}

}
