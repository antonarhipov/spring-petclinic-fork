package org.springframework.samples.petclinic.account;

import java.util.Locale;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

@Component
public class UsernameSuggester {

	public String normalize(String firstName) {
		return firstName == null ? "" : firstName.trim().toLowerCase(Locale.ROOT);
	}

	public String suggest(String firstName, Predicate<String> taken) {
		String base = normalize(firstName);
		if (base.isEmpty()) {
			throw new IllegalArgumentException("Username base must not be blank");
		}
		if (!taken.test(base)) {
			return base;
		}
		int suffix = 2;
		String candidate = base + suffix;
		while (taken.test(candidate)) {
			suffix++;
			candidate = base + suffix;
		}
		return candidate;
	}

}
