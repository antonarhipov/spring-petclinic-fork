package org.springframework.samples.petclinic.scheduling.request;

public final class SchedulingProsePolicy {

	public static final int MIN_LENGTH = 10;

	public static final int MAX_LENGTH = 2000;

	private SchedulingProsePolicy() {
	}

	public static void validate(String prose) {
		if (prose == null || prose.isBlank()) {
			throw new IllegalArgumentException("Scheduling request description is required");
		}
		int length = prose.length();
		if (length < MIN_LENGTH || length > MAX_LENGTH) {
			throw new IllegalArgumentException("Scheduling request description must be between 10 and 2000 characters");
		}
		if (prose.indexOf('<') >= 0 || prose.indexOf('>') >= 0) {
			throw new IllegalArgumentException("Scheduling request description must be plain text without markup");
		}
	}

}
