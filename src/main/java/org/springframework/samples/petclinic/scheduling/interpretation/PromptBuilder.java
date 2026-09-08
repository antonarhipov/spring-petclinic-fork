package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PromptBuilder {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public PromptBuilder(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public String build(String requestText) {
		return build(requestText, LocalDate.now(this.clock));
	}

	@Transactional(readOnly = true)
	public String build(String requestText, LocalDate referenceDate) {
		ConsentDisclosure disclosure = disclosure(referenceDate);
		StringJoiner prompt = new StringJoiner("\n");
		prompt.add("requestText=" + requestText);
		prompt.add("specialties=" + disclosure.specialties());
		prompt.add("veterinarians=" + disclosure.veterinarians());
		prompt.add("openingHours=" + disclosure.openingHours());
		prompt.add("partsOfDay=" + disclosure.partsOfDay());
		prompt.add("today=" + disclosure.today());
		prompt.add("upcomingWeekdayDates=" + upcomingWeekdayDates(disclosure.today()));
		prompt.add("zone=" + disclosure.zone());
		prompt.add("durationMinutes=" + disclosure.durationBounds());
		return prompt.toString();
	}

	@Transactional(readOnly = true)
	public ConsentDisclosure disclosure() {
		return disclosure(LocalDate.now(this.clock));
	}

	@Transactional(readOnly = true)
	public ConsentDisclosure disclosure(LocalDate referenceDate) {
		Map<String, Object> settings = this.jdbc.queryForMap("select * from clinic_settings");
		String durationBounds = "min:" + settings.get("MINIMUM_DURATION_MINUTES") + ",default:"
				+ settings.get("DEFAULT_DURATION_MINUTES") + ",max:" + settings.get("MAXIMUM_DURATION_MINUTES");
		return new ConsentDisclosure(String.join(",", values("select name from specialties order by id")),
				veterinarians(), openingHours(), partsOfDay(), referenceDate, settings.get("TIME_ZONE").toString(),
				durationBounds);
	}

	public record ConsentDisclosure(String specialties, String veterinarians, String openingHours, String partsOfDay,
			LocalDate today, String zone, String durationBounds) {
	}

	private static String upcomingWeekdayDates(LocalDate referenceDate) {
		return Arrays.stream(DayOfWeek.values())
			.map(day -> day + ":" + referenceDate.with(TemporalAdjusters.next(day)))
			.collect(Collectors.joining(";"));
	}

	private List<String> values(String sql) {
		return this.jdbc.query(sql, (result, row) -> result.getString(1));
	}

	private String veterinarians() {
		List<Map<String, Object>> rows = this.jdbc.queryForList("""
				select vet.id, vet.first_name, vet.last_name, specialty.name specialty
				from vets vet
				left join vet_specialties link on link.vet_id = vet.id
				left join specialties specialty on specialty.id = link.specialty_id
				order by vet.id, specialty.id
				""");
		StringJoiner vets = new StringJoiner(";");
		Integer currentId = null;
		StringJoiner specialties = null;
		String name = null;
		for (Map<String, Object> row : rows) {
			Integer id = ((Number) row.get("ID")).intValue();
			if (!id.equals(currentId)) {
				if (currentId != null) {
					vets.add(currentId + ":" + name + "[" + specialties + "]");
				}
				currentId = id;
				name = row.get("FIRST_NAME") + " " + row.get("LAST_NAME");
				specialties = new StringJoiner(",");
			}
			if (row.get("SPECIALTY") != null) {
				specialties.add(row.get("SPECIALTY").toString());
			}
		}
		if (currentId != null) {
			vets.add(currentId + ":" + name + "[" + specialties + "]");
		}
		return vets.toString();
	}

	private String openingHours() {
		return String.join(";", this.jdbc.query("""
				select weekday, open_time, close_time from clinic_opening_hours
				order by case weekday
				 when 'MONDAY' then 1 when 'TUESDAY' then 2 when 'WEDNESDAY' then 3
				 when 'THURSDAY' then 4 when 'FRIDAY' then 5 when 'SATURDAY' then 6 else 7 end
				""", (result, row) -> result.getString("weekday") + ":" + (result.getTime("open_time") == null
				? "closed"
				: result.getTime("open_time").toLocalTime() + "-" + result.getTime("close_time").toLocalTime())));
	}

	private String partsOfDay() {
		return String.join(";", this.jdbc.query("""
				select weekday, open_time, close_time from clinic_opening_hours
				order by case weekday
				 when 'MONDAY' then 1 when 'TUESDAY' then 2 when 'WEDNESDAY' then 3
				 when 'THURSDAY' then 4 when 'FRIDAY' then 5 when 'SATURDAY' then 6 else 7 end
				""", (result, row) -> {
			String weekday = result.getString("weekday");
			if (result.getTime("open_time") == null) {
				return weekday + ":morning=closed,afternoon=closed,evening=closed";
			}
			LocalTime opening = result.getTime("open_time").toLocalTime();
			LocalTime closing = result.getTime("close_time").toLocalTime();
			return weekday + ":" + interval("morning", opening, earlier(closing, LocalTime.NOON)) + ","
					+ interval("afternoon", later(opening, LocalTime.NOON), earlier(closing, LocalTime.of(17, 0))) + ","
					+ interval("evening", later(opening, LocalTime.of(17, 0)), closing);
		}));
	}

	private String interval(String name, LocalTime start, LocalTime end) {
		return name + "=" + (end.isAfter(start) ? start + "-" + end : "closed");
	}

	private LocalTime earlier(LocalTime left, LocalTime right) {
		return left.isBefore(right) ? left : right;
	}

	private LocalTime later(LocalTime left, LocalTime right) {
		return left.isAfter(right) ? left : right;
	}

}
