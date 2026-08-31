package org.springframework.samples.petclinic.account;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class UsernamePolicy {

	public static final int MIN_LENGTH = 3;

	public static final int MAX_LENGTH = 50;

	private static final Pattern VALID_USERNAME_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9._-]*[a-z0-9])?$");

	private static final Pattern CONSECUTIVE_SPECIAL_PATTERN = Pattern.compile("[._-]{2,}");

	public String normalize(String username) {
		if (username == null) {
			return null;
		}
		return username.trim().toLowerCase();
	}

	public boolean isValid(String username) {
		if (username == null) {
			return false;
		}
		String normalized = normalize(username);
		if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
			return false;
		}
		if (!VALID_USERNAME_PATTERN.matcher(normalized).matches()) {
			return false;
		}
		return !CONSECUTIVE_SPECIAL_PATTERN.matcher(normalized).find();
	}

	public void validate(String username) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException("Username must not be blank");
		}
		String normalized = normalize(username);
		if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
			throw new IllegalArgumentException(
					"Username length must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters");
		}
		if (!VALID_USERNAME_PATTERN.matcher(normalized).matches()) {
			throw new IllegalArgumentException(
					"Username must only contain lowercase alphanumeric characters, dots, underscores, or hyphens, and start/end with an alphanumeric character");
		}
		if (CONSECUTIVE_SPECIAL_PATTERN.matcher(normalized).find()) {
			throw new IllegalArgumentException("Username must not contain consecutive special characters");
		}
	}

	public String suggestUsername(String firstName, String lastName) {
		String cleanFirst = firstName != null ? firstName.trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";
		String cleanLast = lastName != null ? lastName.trim().toLowerCase().replaceAll("[^a-z0-9]", "") : "";

		if (!cleanFirst.isEmpty() && !cleanLast.isEmpty()) {
			String candidate = cleanFirst + "." + cleanLast;
			if (candidate.length() > MAX_LENGTH) {
				candidate = candidate.substring(0, MAX_LENGTH);
			}
			return candidate;
		}
		if (!cleanFirst.isEmpty()) {
			return cleanFirst;
		}
		if (!cleanLast.isEmpty()) {
			return cleanLast;
		}
		return "owner";
	}

}
