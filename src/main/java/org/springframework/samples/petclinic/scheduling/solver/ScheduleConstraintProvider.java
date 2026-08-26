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

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import org.springframework.samples.petclinic.scheduling.ai.UrgencyLevel;

public class ScheduleConstraintProvider implements ConstraintProvider {

	@Override
	public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
		return new Constraint[] { noOverlapWithBookedSlots(constraintFactory), withinVetAvailability(constraintFactory),
				noExcludedSlots(constraintFactory), preferRequestedTimeWindow(constraintFactory),
				earlierIsBetter(constraintFactory) };
	}

	Constraint noOverlapWithBookedSlots(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(ProposedBooking.class)
			.filter(booking -> booking.getSelectedSlot() != null)
			.join(BookedSlot.class, Joiners.equal(booking -> booking.getSelectedSlot().getVetId(), BookedSlot::vetId))
			.filter((booking, booked) -> booked.overlapsWith(booking.getSelectedSlot()))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("No overlap with booked slots");
	}

	Constraint withinVetAvailability(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(ProposedBooking.class)
			.filter(booking -> booking.getSelectedSlot() != null)
			.ifNotExists(VetAvailability.class,
					Joiners.equal(booking -> booking.getSelectedSlot().getVetId(), VetAvailability::vetId),
					Joiners.filtering((booking, avail) -> avail.covers(booking.getSelectedSlot())))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("Within vet availability");
	}

	Constraint noExcludedSlots(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(ProposedBooking.class)
			.filter(booking -> booking.getSelectedSlot() != null)
			.join(ExcludedSlot.class,
					Joiners.equal(booking -> booking.getSelectedSlot().getVetId(), ExcludedSlot::vetId),
					Joiners.equal(booking -> booking.getSelectedSlot().getStartTime(), ExcludedSlot::startTime))
			.penalize(HardMediumSoftScore.ONE_HARD)
			.asConstraint("No excluded slots");
	}

	/**
	 * Prefers slots that fall inside one of the owner's requested windows (e.g. a
	 * specific date, day of week, or part of day such as "after lunch" = afternoon). This
	 * is a medium-level penalty so it outranks the "earlier is better" soft tie-breaker:
	 * a matching afternoon slot is chosen over an earlier morning slot. When the owner
	 * did not express any preference the constraint never fires.
	 */
	Constraint preferRequestedTimeWindow(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(ProposedBooking.class)
			.filter(booking -> booking.getSelectedSlot() != null && !booking.getPreferredWindows().isEmpty()
					&& booking.getPreferredWindows()
						.stream()
						.noneMatch(window -> window.matches(booking.getSelectedSlot())))
			.penalize(HardMediumSoftScore.ONE_MEDIUM)
			.asConstraint("Prefer requested time window");
	}

	Constraint earlierIsBetter(ConstraintFactory constraintFactory) {
		return constraintFactory.forEach(ProposedBooking.class)
			.filter(booking -> booking.getSelectedSlot() != null)
			.penalize(HardMediumSoftScore.ONE_SOFT, booking -> {
				int baseScore = booking.getSelectedSlot().getStartTime().getDayOfYear() * 24
						+ booking.getSelectedSlot().getStartTime().getHour();
				int weight = (booking.getUrgency() == UrgencyLevel.URGENT) ? 100 : 1;
				return baseScore * weight;
			})
			.asConstraint("Earlier is better");
	}

}
