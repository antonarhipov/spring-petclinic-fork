package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class EmergencyKeywordScreen {

	private static final Pattern EMERGENCY_PATTERN = Pattern.compile(
			"\\b(bleeding heavily|heavy bleeding|profuse bleeding|unconscious|poison(?:ed|ing)?|toxic|difficulty breathing|trouble breathing|not breathing|cannot breathe|seizures?|collapsed?|choking|trauma|hit by car|pale gums|bloat(?:ed)?|snake\\s*bite|electric shock|unresponsive)\\b",
			Pattern.CASE_INSENSITIVE);

	public EmergencyScreenResult screen(String prose) {
		if (prose == null || prose.isBlank()) {
			return new EmergencyScreenResult(false, Collections.emptyList(), null);
		}

		Matcher matcher = EMERGENCY_PATTERN.matcher(prose);
		List<String> matchedKeywords = new ArrayList<>();
		while (matcher.find()) {
			matchedKeywords.add(matcher.group().toLowerCase());
		}

		if (!matchedKeywords.isEmpty()) {
			return new EmergencyScreenResult(true, matchedKeywords, UrgentCareGuidance.defaultGuidance());
		}
		return new EmergencyScreenResult(false, Collections.emptyList(), null);
	}

	public record EmergencyScreenResult(boolean emergencyDetected, List<String> matchedKeywords,
			UrgentCareGuidance guidance) {
	}

}
