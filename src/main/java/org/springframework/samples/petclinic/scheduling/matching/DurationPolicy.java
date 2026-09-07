package org.springframework.samples.petclinic.scheduling.matching;

public class DurationPolicy {

	public static final String CLAMPED_MESSAGE_KEY = "scheduling.interpretation.duration.clamped";

	public record DurationResult(int effectiveDuration, boolean clamped, String clampNoteKey) {
	}

	public static DurationResult resolve(Integer rawDurationMinutes, int minDuration, int defaultDuration,
			int maxDuration) {
		if (rawDurationMinutes == null) {
			return new DurationResult(defaultDuration, false, null);
		}
		if (rawDurationMinutes < minDuration) {
			return new DurationResult(minDuration, true, CLAMPED_MESSAGE_KEY);
		}
		if (rawDurationMinutes > maxDuration) {
			return new DurationResult(maxDuration, true, CLAMPED_MESSAGE_KEY);
		}
		return new DurationResult(rawDurationMinutes, false, null);
	}

}
