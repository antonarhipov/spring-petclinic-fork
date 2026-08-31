package org.springframework.samples.petclinic.config;

import java.time.Duration;
import java.util.List;

import ai.timefold.solver.core.api.score.buildin.bendable.BendableScore;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import ai.timefold.solver.core.config.localsearch.LocalSearchPhaseConfig;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.EnvironmentMode;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.samples.petclinic.scheduling.matching.AppointmentAssignment;
import org.springframework.samples.petclinic.scheduling.matching.AppointmentSchedulingConstraintProvider;
import org.springframework.samples.petclinic.scheduling.matching.AppointmentSchedulingSolution;

@Configuration
public class TimefoldConfiguration {

	@Bean
	public SolverFactory<AppointmentSchedulingSolution> appointmentSchedulingSolverFactory() {
		SolverConfig solverConfig = new SolverConfig().withSolutionClass(AppointmentSchedulingSolution.class)
			.withEntityClasses(AppointmentAssignment.class)
			.withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
				.withConstraintProviderClass(AppointmentSchedulingConstraintProvider.class))
			.withEnvironmentMode(EnvironmentMode.REPRODUCIBLE)
			.withRandomSeed(0L)
			.withMoveThreadCount("NONE")
			.withPhaseList(List.of(new ConstructionHeuristicPhaseConfig(), new LocalSearchPhaseConfig()))
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(5))
				.withUnimprovedSpentLimit(Duration.ofMillis(200)));

		return SolverFactory.create(solverConfig);
	}

}
