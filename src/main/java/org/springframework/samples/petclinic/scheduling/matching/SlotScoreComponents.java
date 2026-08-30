package org.springframework.samples.petclinic.scheduling.matching;

import java.util.List;

import ai.timefold.solver.core.api.score.BendableScore;

public record SlotScoreComponents(BendableScore score, String publicExplanationCode, List<NamedComponent> components) {

	public record NamedComponent(String name, String level, int value) {
	}

}
