package org.springframework.samples.petclinic.scheduling.matching;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicType;
import ai.timefold.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicForagerConfig;
import ai.timefold.solver.core.config.constructionheuristic.decider.forager.ConstructionHeuristicPickEarlyType;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;

@Configuration
public class TimefoldSolverConfiguration {

	@Bean
	public SolverFactory<SlotSelectionSolution> slotSelectionSolverFactory() {
		ConstructionHeuristicPhaseConfig construction = new ConstructionHeuristicPhaseConfig();
		construction.setConstructionHeuristicType(ConstructionHeuristicType.ALLOCATE_ENTITY_FROM_QUEUE);
		ConstructionHeuristicForagerConfig forager = new ConstructionHeuristicForagerConfig();
		forager.setPickEarlyType(ConstructionHeuristicPickEarlyType.NEVER);
		construction.setForagerConfig(forager);
		SolverConfig config = new SolverConfig().withSolutionClass(SlotSelectionSolution.class)
			.withEntityClasses(SlotAssignment.class)
			.withEasyScoreCalculatorClass(SlotEasyScoreCalculator.class)
			.withEnvironmentMode(EnvironmentMode.PHASE_ASSERT)
			.withRandomSeed(0L)
			.withMoveThreadCount("NONE")
			.withPhases(construction)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(java.time.Duration.ofSeconds(5)));
		return SolverFactory.create(config);
	}

}
