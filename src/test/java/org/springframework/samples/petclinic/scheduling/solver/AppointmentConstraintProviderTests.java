package org.springframework.samples.petclinic.scheduling.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AppointmentConstraintProviderTests {

	@Test
	void planningSolutionContainsExactlyTheGeneratedCandidateRange() {
		AppointmentPlanningEntity selection = new AppointmentPlanningEntity();
		AppointmentPlanningSolution solution = new AppointmentPlanningSolution(List.of(), List.of(selection));
		assertThat(solution.getSelections()).containsExactly(selection);
		assertThat(solution.getCandidates()).isEmpty();
	}

}
