package org.springframework.samples.petclinic.scheduling.request;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.persistence.EntityManager;

@Component
public class InterpretationViewMapper {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactionTemplate;

	private final EntityManager entityManager;

	@Autowired
	public InterpretationViewMapper(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
			EntityManager entityManager) {
		this.jdbc = jdbc;
		this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
		this.entityManager = entityManager;
	}

	public InterpretationViewMapper(JdbcTemplate jdbc) {
		this(jdbc, null, null);
	}

	public record WindowView(String dayOrDate, String startTime, String endTime) {
	}

	public record ReviewView(String careTypeMessageKey, String specialty, String specialtyLabel,
			boolean unmatchedSpecialty, Integer requestedDuration, int effectiveDuration, boolean clamped,
			String preferredVetName, String originMessageKey, List<WindowView> preferredWindows,
			List<WindowView> allowedWindows, List<WindowView> excludedWindows) {
	}

	public ReviewView toReviewView(SchedulingRequest request, Locale locale) {
		if (this.transactionTemplate != null && this.entityManager != null) {
			return this.transactionTemplate.execute(status -> {
				Integer interpId = request.getCurrentInterpretation() != null
						? request.getCurrentInterpretation().getId() : null;
				if (interpId == null) {
					return null;
				}
				Interpretation interpretation = this.entityManager.find(Interpretation.class, interpId);
				if (interpretation == null) {
					return null;
				}
				return buildReviewView(interpretation, locale);
			});
		}
		Interpretation interpretation = request.getCurrentInterpretation();
		if (interpretation == null) {
			return null;
		}
		return buildReviewView(interpretation, locale);
	}

	public ReviewView toReviewView(Interpretation interpretation, Locale locale) {
		if (this.transactionTemplate != null && this.entityManager != null && interpretation.getId() != null) {
			return this.transactionTemplate.execute(status -> {
				Interpretation reloaded = this.entityManager.find(Interpretation.class, interpretation.getId());
				return buildReviewView(reloaded != null ? reloaded : interpretation, locale);
			});
		}
		return buildReviewView(interpretation, locale);
	}

	private ReviewView buildReviewView(Interpretation interpretation, Locale locale) {
		Locale targetLocale = locale != null ? locale : Locale.ENGLISH;
		DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm", targetLocale);
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
			.withLocale(targetLocale);

		CareType careTypeEnum = interpretation.getEffectiveCareType();
		String careTypeMessageKey = careTypeEnum == CareType.SPECIALTY ? "scheduling.careType.specialty"
				: "scheduling.careType.general";

		String specialty = interpretation.getSpecialty();
		String specialtyLabel = interpretation.getSpecialtyLabel();
		boolean unmatchedSpecialty = interpretation.isOtherSpecialty();
		Map<String, Object> settings = querySettings();
		int minDuration = ((Number) settings.get("minimum_duration_minutes")).intValue();
		int defaultDuration = ((Number) settings.get("default_duration_minutes")).intValue();
		int maxDuration = ((Number) settings.get("maximum_duration_minutes")).intValue();

		Integer requestedDuration = interpretation.getDurationMinutes();
		int effectiveDuration = interpretation.getEffectiveDurationMinutes(defaultDuration);
		boolean clamped = requestedDuration != null
				&& (requestedDuration < minDuration || requestedDuration > maxDuration);
		if (clamped) {
			effectiveDuration = Math.max(minDuration, Math.min(maxDuration, requestedDuration));
		}

		String preferredVetName = interpretation.getPreferredVet() != null
				? interpretation.getPreferredVet().getFirstName() + " " + interpretation.getPreferredVet().getLastName()
				: null;

		String originMessageKey = interpretation.getOrigin() == InterpretationOrigin.STAFF
				? "scheduling.interpretation.origin.staff" : "scheduling.interpretation.origin.ai";

		List<WindowView> preferred = mapWindows(interpretation.getPreferredWindows(), targetLocale, dateFormatter,
				timeFormatter);
		List<WindowView> allowed = mapWindows(interpretation.getAllowedWindows(), targetLocale, dateFormatter,
				timeFormatter);
		List<WindowView> excluded = mapWindows(interpretation.getExcludedWindows(), targetLocale, dateFormatter,
				timeFormatter);

		return new ReviewView(careTypeMessageKey, specialty, specialtyLabel, unmatchedSpecialty, requestedDuration,
				effectiveDuration, clamped, preferredVetName, originMessageKey, preferred, allowed, excluded);
	}

	private List<WindowView> mapWindows(List<InterpretationWindow> windows, Locale locale,
			DateTimeFormatter dateFormatter, DateTimeFormatter timeFormatter) {
		List<WindowView> list = new ArrayList<>();
		if (windows != null) {
			for (InterpretationWindow window : windows) {
				String dayOrDate = formatDayOrDate(window, locale, dateFormatter);
				String start = window.getStartTime() != null ? window.getStartTime().format(timeFormatter) : "";
				String end = window.getEndTime() != null ? window.getEndTime().format(timeFormatter) : "";
				list.add(new WindowView(dayOrDate, start, end));
			}
		}
		return list;
	}

	private String formatDayOrDate(InterpretationWindow window, Locale locale, DateTimeFormatter dateFormatter) {
		if (window.getWeekday() != null) {
			return window.getWeekday().getDisplayName(TextStyle.FULL, locale);
		}
		if (window.getDate() != null) {
			return window.getDate().format(dateFormatter);
		}
		return "";
	}

	private Map<String, Object> querySettings() {
		if (this.jdbc == null) {
			throw new IllegalStateException("Clinic settings are required to render an interpretation");
		}
		return this.jdbc.queryForMap(
				"select minimum_duration_minutes, default_duration_minutes, maximum_duration_minutes from clinic_settings");
	}

}
