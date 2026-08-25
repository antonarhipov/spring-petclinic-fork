/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.solver;

import java.time.Duration;
import java.util.UUID;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SchedulingSolverConfig {

	@Bean
	public SolverManager<AppointmentScheduleSolution, UUID> solverManager() {
		SolverConfig solverConfig = new SolverConfig().withSolutionClass(AppointmentScheduleSolution.class)
			.withEntityClasses(ProposedBooking.class)
			.withConstraintProviderClass(ScheduleConstraintProvider.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));
		SolverFactory<AppointmentScheduleSolution> solverFactory = SolverFactory.create(solverConfig);
		return SolverManager.create(solverFactory);
	}

}
