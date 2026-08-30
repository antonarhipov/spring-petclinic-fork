package org.springframework.samples.petclinic.scheduling.matching;

import java.util.List;
import java.util.stream.Collectors;

public final class TimefoldExecutionEvidence {

	private TimefoldExecutionEvidence() {
	}

	public static String scoreJson(SlotScoreComponents components) {
		if (components == null || components.score() == null) {
			return "{}";
		}
		return "{\"hard\":[" + components.score().hardScore(0) + "],\"soft\":[" + components.score().softScore(0) + ","
				+ components.score().softScore(1) + "," + components.score().softScore(2) + ","
				+ components.score().softScore(3) + "]}";
	}

	public static String explanationJson(SlotScoreComponents components) {
		if (components == null) {
			return "[]";
		}
		return components.components()
			.stream()
			.map(item -> "{\"name\":\"" + item.name() + "\",\"level\":\"" + item.level() + "\",\"value\":"
					+ item.value() + "}")
			.collect(Collectors.joining(",", "[", "]"));
	}

	public static List<String> publicCodes() {
		return List.of("PREFERRED_TIME_AND_VETERINARIAN", "PREFERRED_TIME", "PREFERRED_VETERINARIAN",
				"EARLIEST_AVAILABLE", "FALLBACK_SLOT", "NONE");
	}

}
