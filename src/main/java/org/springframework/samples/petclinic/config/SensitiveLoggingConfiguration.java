package org.springframework.samples.petclinic.config;

import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SensitiveLoggingConfiguration {

	private static final Pattern PASSWORD_PATTERN = Pattern
		.compile("(?i)(password|passwd|secret|token|apiKey|wrappedKey|nonce)=['\"]?([^'\"&\\s]+)['\"]?");

	private static final Pattern CLINICAL_PATTERN = Pattern
		.compile("(?i)(clinicalNotes|internalReason|reason)=['\"]?([^'\"&\\s]+)['\"]?");

	public static String maskSensitiveData(String input) {
		if (input == null) {
			return null;
		}
		String masked = PASSWORD_PATTERN.matcher(input).replaceAll("$1=[REDACTED]");
		return CLINICAL_PATTERN.matcher(masked).replaceAll("$1=[REDACTED]");
	}

	public static String redactProse(String prose) {
		if (prose == null) {
			return null;
		}
		return "[REDACTED_PROSE len=" + prose.length() + "]";
	}

	public static String redactClinical(String notes) {
		if (notes == null) {
			return null;
		}
		return "[REDACTED_CLINICAL len=" + notes.length() + "]";
	}

	public static String redactCredential(String credential) {
		if (credential == null) {
			return null;
		}
		return "[REDACTED_CREDENTIAL]";
	}

	public static String redactKey(String key) {
		if (key == null) {
			return null;
		}
		return "[REDACTED_KEY]";
	}

}
