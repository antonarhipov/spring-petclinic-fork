package org.springframework.samples.petclinic.scheduling.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import org.springframework.stereotype.Component;

/**
 * Candidate slots are already filtered for hard availability rules. The bounded selection
 * service deterministically applies the soft ordering before persisting a hold,
 * preserving Timefold's one-request planning boundary without moving bookings.
 */
@Component
public class AppointmentConstraintProvider implements ConstraintProvider {

	@Override
	public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
		return new Constraint[0];
	}

}
