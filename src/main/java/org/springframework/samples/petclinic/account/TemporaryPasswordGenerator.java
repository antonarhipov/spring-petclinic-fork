package org.springframework.samples.petclinic.account;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TemporaryPasswordGenerator {

	private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // omitted I, O
																		// for clarity

	private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz"; // omitted l for
																			// clarity

	private static final String DIGITS = "23456789"; // omitted 0, 1 for clarity

	private static final String SPECIAL = "!@#$%&*+-=";

	private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

	public static final int DEFAULT_PASSWORD_LENGTH = 14;

	public static final int EXPIRY_DAYS = 7;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generateTemporaryPassword() {
		return generateTemporaryPassword(DEFAULT_PASSWORD_LENGTH);
	}

	public String generateTemporaryPassword(int length) {
		if (length < 8) {
			length = 8;
		}

		List<Character> chars = new ArrayList<>(length);
		chars.add(UPPERCASE.charAt(this.secureRandom.nextInt(UPPERCASE.length())));
		chars.add(LOWERCASE.charAt(this.secureRandom.nextInt(LOWERCASE.length())));
		chars.add(DIGITS.charAt(this.secureRandom.nextInt(DIGITS.length())));
		chars.add(SPECIAL.charAt(this.secureRandom.nextInt(SPECIAL.length())));

		for (int i = 4; i < length; i++) {
			chars.add(ALL_CHARS.charAt(this.secureRandom.nextInt(ALL_CHARS.length())));
		}

		Collections.shuffle(chars, this.secureRandom);

		StringBuilder password = new StringBuilder(length);
		for (char c : chars) {
			password.append(c);
		}
		return password.toString();
	}

	public Instant calculateExpiryInstant() {
		return Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS);
	}

}
