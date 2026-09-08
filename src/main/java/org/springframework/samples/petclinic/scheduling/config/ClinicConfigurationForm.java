package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ClinicConfigurationForm {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private Integer bookingHorizonDays;

	private Integer minimumLeadDays;

	private Integer minimumDurationMinutes;

	private Integer defaultDurationMinutes;

	private Integer maximumDurationMinutes;

	private String morningStart;

	private String morningEnd;

	private String afternoonStart;

	private String afternoonEnd;

	private String eveningStart;

	private String eveningEnd;

	private String timeZone;

	private List<OpeningHoursValue> openingHours = new ArrayList<>();

	private String workingPeriods;

	private String exceptions;

	private String leavePeriods;

	private String closures;

	public static ClinicConfigurationForm from(ClinicConfiguration source) {
		ClinicConfigurationForm form = new ClinicConfigurationForm();
		form.bookingHorizonDays = source.bookingHorizonDays();
		form.minimumLeadDays = source.minimumLeadDays();
		form.minimumDurationMinutes = source.minimumDurationMinutes();
		form.defaultDurationMinutes = source.defaultDurationMinutes();
		form.maximumDurationMinutes = source.maximumDurationMinutes();
		form.morningStart = format(source.morningStart());
		form.morningEnd = format(source.morningEnd());
		form.afternoonStart = format(source.afternoonStart());
		form.afternoonEnd = format(source.afternoonEnd());
		form.eveningStart = format(source.eveningStart());
		form.eveningEnd = format(source.eveningEnd());
		form.timeZone = source.timeZone();
		form.openingHours = source.openingHours()
			.stream()
			.map(OpeningHoursValue::from)
			.collect(Collectors.toCollection(ArrayList::new));
		form.workingPeriods = source.workingPeriods()
			.stream()
			.map(value -> value.veterinarianId() + "," + value.weekday() + "," + format(value.startTime()) + ","
					+ format(value.endTime()))
			.collect(Collectors.joining("\n"));
		form.exceptions = source.exceptions()
			.stream()
			.map(value -> value.veterinarianId() + "," + value.date())
			.collect(Collectors.joining("\n"));
		form.leavePeriods = source.leavePeriods()
			.stream()
			.map(value -> value.veterinarianId() + "," + value.startDate() + "," + value.endDate())
			.collect(Collectors.joining("\n"));
		form.closures = source.closures().stream().map(LocalDate::toString).collect(Collectors.joining("\n"));
		return form;
	}

	public ClinicConfiguration toConfiguration(int gridMinutes) {
		if (this.bookingHorizonDays == null || this.minimumLeadDays == null || this.minimumDurationMinutes == null
				|| this.defaultDurationMinutes == null || this.maximumDurationMinutes == null) {
			throw invalid("scheduling.settings.validation.required");
		}
		List<ClinicConfiguration.OpeningPeriod> parsedHours = this.openingHours.stream()
			.map(OpeningHoursValue::toValue)
			.toList();
		return new ClinicConfiguration(this.bookingHorizonDays, this.minimumLeadDays, this.minimumDurationMinutes,
				this.defaultDurationMinutes, this.maximumDurationMinutes, gridMinutes, parseTime(this.morningStart),
				parseTime(this.morningEnd), parseTime(this.afternoonStart), parseTime(this.afternoonEnd),
				parseTime(this.eveningStart), parseTime(this.eveningEnd), blankToNull(this.timeZone), parsedHours,
				parseWorkingPeriods(this.workingPeriods), parseExceptions(this.exceptions),
				parseLeave(this.leavePeriods), parseClosures(this.closures));
	}

	private List<ClinicConfiguration.WorkingPeriod> parseWorkingPeriods(String value) {
		return lines(value).stream().map(line -> {
			String[] fields = fields(line, 4, "scheduling.settings.validation.workingFormat");
			return new ClinicConfiguration.WorkingPeriod(parseVeterinarianId(fields[0]), parseWeekday(fields[1]),
					parseTime(fields[2]), parseTime(fields[3]));
		}).toList();
	}

	private List<ClinicConfiguration.VetExceptionDate> parseExceptions(String value) {
		return lines(value).stream().map(line -> {
			String[] fields = fields(line, 2, "scheduling.settings.validation.exceptionFormat");
			return new ClinicConfiguration.VetExceptionDate(parseVeterinarianId(fields[0]), parseDate(fields[1]));
		}).toList();
	}

	private List<ClinicConfiguration.LeavePeriod> parseLeave(String value) {
		return lines(value).stream().map(line -> {
			String[] fields = fields(line, 3, "scheduling.settings.validation.leaveFormat");
			return new ClinicConfiguration.LeavePeriod(parseVeterinarianId(fields[0]), parseDate(fields[1]),
					parseDate(fields[2]));
		}).toList();
	}

	private List<LocalDate> parseClosures(String value) {
		return lines(value).stream().map(this::parseDate).toList();
	}

	private List<String> lines(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return value.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
	}

	private String[] fields(String line, int count, String messageKey) {
		String[] values = line.split(",", -1);
		if (values.length != count) {
			throw invalid(messageKey);
		}
		for (int index = 0; index < values.length; index++) {
			values[index] = values[index].trim();
			if (values[index].isEmpty()) {
				throw invalid(messageKey);
			}
		}
		return values;
	}

	private int parseVeterinarianId(String value) {
		try {
			int parsed = Integer.parseInt(value);
			if (parsed <= 0) {
				throw invalid("scheduling.settings.validation.veterinarian");
			}
			return parsed;
		}
		catch (NumberFormatException ex) {
			throw invalid("scheduling.settings.validation.veterinarian");
		}
	}

	private DayOfWeek parseWeekday(String value) {
		try {
			return DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw invalid("scheduling.settings.validation.weekday");
		}
	}

	private LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value);
		}
		catch (DateTimeParseException ex) {
			throw invalid("scheduling.settings.validation.date");
		}
	}

	private static LocalTime parseTime(String value) {
		try {
			return LocalTime.parse(value, TIME_FORMAT);
		}
		catch (DateTimeParseException | NullPointerException ex) {
			throw invalid("scheduling.settings.validation.time");
		}
	}

	private static String format(LocalTime value) {
		return value == null ? "" : value.format(TIME_FORMAT);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static ConfigurationValidationException invalid(String messageKey) {
		return new ConfigurationValidationException(messageKey);
	}

	public Integer getBookingHorizonDays() {
		return this.bookingHorizonDays;
	}

	public void setBookingHorizonDays(Integer bookingHorizonDays) {
		this.bookingHorizonDays = bookingHorizonDays;
	}

	public Integer getMinimumLeadDays() {
		return this.minimumLeadDays;
	}

	public void setMinimumLeadDays(Integer minimumLeadDays) {
		this.minimumLeadDays = minimumLeadDays;
	}

	public Integer getMinimumDurationMinutes() {
		return this.minimumDurationMinutes;
	}

	public void setMinimumDurationMinutes(Integer minimumDurationMinutes) {
		this.minimumDurationMinutes = minimumDurationMinutes;
	}

	public Integer getDefaultDurationMinutes() {
		return this.defaultDurationMinutes;
	}

	public void setDefaultDurationMinutes(Integer defaultDurationMinutes) {
		this.defaultDurationMinutes = defaultDurationMinutes;
	}

	public Integer getMaximumDurationMinutes() {
		return this.maximumDurationMinutes;
	}

	public void setMaximumDurationMinutes(Integer maximumDurationMinutes) {
		this.maximumDurationMinutes = maximumDurationMinutes;
	}

	public String getMorningStart() {
		return this.morningStart;
	}

	public void setMorningStart(String morningStart) {
		this.morningStart = morningStart;
	}

	public String getMorningEnd() {
		return this.morningEnd;
	}

	public void setMorningEnd(String morningEnd) {
		this.morningEnd = morningEnd;
	}

	public String getAfternoonStart() {
		return this.afternoonStart;
	}

	public void setAfternoonStart(String afternoonStart) {
		this.afternoonStart = afternoonStart;
	}

	public String getAfternoonEnd() {
		return this.afternoonEnd;
	}

	public void setAfternoonEnd(String afternoonEnd) {
		this.afternoonEnd = afternoonEnd;
	}

	public String getEveningStart() {
		return this.eveningStart;
	}

	public void setEveningStart(String eveningStart) {
		this.eveningStart = eveningStart;
	}

	public String getEveningEnd() {
		return this.eveningEnd;
	}

	public void setEveningEnd(String eveningEnd) {
		this.eveningEnd = eveningEnd;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public List<OpeningHoursValue> getOpeningHours() {
		return this.openingHours;
	}

	public void setOpeningHours(List<OpeningHoursValue> openingHours) {
		this.openingHours = openingHours;
	}

	public String getWorkingPeriods() {
		return this.workingPeriods;
	}

	public void setWorkingPeriods(String workingPeriods) {
		this.workingPeriods = workingPeriods;
	}

	public String getExceptions() {
		return this.exceptions;
	}

	public void setExceptions(String exceptions) {
		this.exceptions = exceptions;
	}

	public String getLeavePeriods() {
		return this.leavePeriods;
	}

	public void setLeavePeriods(String leavePeriods) {
		this.leavePeriods = leavePeriods;
	}

	public String getClosures() {
		return this.closures;
	}

	public void setClosures(String closures) {
		this.closures = closures;
	}

	public static class OpeningHoursValue {

		private DayOfWeek weekday;

		private boolean closed;

		private String openTime;

		private String closeTime;

		static OpeningHoursValue from(ClinicConfiguration.OpeningPeriod source) {
			OpeningHoursValue value = new OpeningHoursValue();
			value.weekday = source.weekday();
			value.closed = !source.isOpen();
			value.openTime = format(source.openTime());
			value.closeTime = format(source.closeTime());
			return value;
		}

		ClinicConfiguration.OpeningPeriod toValue() {
			if (this.weekday == null) {
				throw invalid("scheduling.settings.validation.weekdays");
			}
			if (this.closed) {
				return new ClinicConfiguration.OpeningPeriod(this.weekday, null, null);
			}
			return new ClinicConfiguration.OpeningPeriod(this.weekday, parseTime(this.openTime),
					parseTime(this.closeTime));
		}

		public DayOfWeek getWeekday() {
			return this.weekday;
		}

		public String getWeekdayMessageKey() {
			if (this.weekday == null) {
				return "scheduling.settings.validation.weekday";
			}
			return switch (this.weekday) {
				case MONDAY -> "scheduling.weekday.monday";
				case TUESDAY -> "scheduling.weekday.tuesday";
				case WEDNESDAY -> "scheduling.weekday.wednesday";
				case THURSDAY -> "scheduling.weekday.thursday";
				case FRIDAY -> "scheduling.weekday.friday";
				case SATURDAY -> "scheduling.weekday.saturday";
				case SUNDAY -> "scheduling.weekday.sunday";
			};
		}

		public void setWeekday(DayOfWeek weekday) {
			this.weekday = weekday;
		}

		public boolean isClosed() {
			return this.closed;
		}

		public void setClosed(boolean closed) {
			this.closed = closed;
		}

		public String getOpenTime() {
			return this.openTime;
		}

		public void setOpenTime(String openTime) {
			this.openTime = openTime;
		}

		public String getCloseTime() {
			return this.closeTime;
		}

		public void setCloseTime(String closeTime) {
			this.closeTime = closeTime;
		}

	}

}
