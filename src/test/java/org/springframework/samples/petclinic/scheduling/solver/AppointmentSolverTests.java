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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import org.junit.jupiter.api.Test;

class AppointmentSolverTests {

	@Test
	void shouldSolveSingleRequestAgainstFrozenCalendarWithinOneSecond() throws Exception {
		Integer vetId = 1;
		LocalDateTime date = LocalDateTime.of(2026, 9, 1, 0, 0);

		// Vet availability: 09:00 - 17:00
		VetAvailability availability = new VetAvailability(vetId, date.withHour(9).withMinute(0),
				date.withHour(17).withMinute(0));

		// Two fixed/confirmed bookings: 09:00 - 10:00 and 10:00 - 11:00
		BookedSlot booked1 = new BookedSlot(vetId, date.withHour(9).withMinute(0), date.withHour(10).withMinute(0));
		BookedSlot booked2 = new BookedSlot(vetId, date.withHour(10).withMinute(0), date.withHour(11).withMinute(0));

		// Candidate 30-min slots
		List<CandidateSlot> candidateSlots = List.of(new CandidateSlot(vetId, date.withHour(9).withMinute(0), 30),
				new CandidateSlot(vetId, date.withHour(9).withMinute(30), 30),
				new CandidateSlot(vetId, date.withHour(10).withMinute(0), 30),
				new CandidateSlot(vetId, date.withHour(10).withMinute(30), 30),
				new CandidateSlot(vetId, date.withHour(11).withMinute(0), 30),
				new CandidateSlot(vetId, date.withHour(11).withMinute(30), 30),
				new CandidateSlot(vetId, date.withHour(17).withMinute(0), 30));

		ProposedBooking booking = new ProposedBooking(100, 30);

		AppointmentScheduleSolution problem = new AppointmentScheduleSolution(List.of(booked1, booked2),
				List.of(availability), candidateSlots, booking);

		SolverConfig solverConfig = new SolverConfig().withSolutionClass(AppointmentScheduleSolution.class)
			.withEntityClasses(ProposedBooking.class)
			.withConstraintProviderClass(ScheduleConstraintProvider.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

		SolverFactory<AppointmentScheduleSolution> solverFactory = SolverFactory.create(solverConfig);
		try (SolverManager<AppointmentScheduleSolution, UUID> solverManager = SolverManager.create(solverFactory)) {
			UUID problemId = UUID.randomUUID();
			SolverJob<AppointmentScheduleSolution, UUID> solverJob = solverManager.solve(problemId, problem);

			AppointmentScheduleSolution solution = solverJob.getFinalBestSolution();

			assertThat(solution.getScore()).isNotNull();
			assertThat(solution.getScore().isFeasible()).isTrue();
			assertThat(solution.getScore().hardScore()).isEqualTo(0);

			CandidateSlot selected = solution.getProposedBooking().getSelectedSlot();
			assertThat(selected).isNotNull();
			assertThat(selected.getVetId()).isEqualTo(vetId);
			// 09:00-10:00 and 10:00-11:00 are booked, so earliest feasible slot is 11:00
			assertThat(selected.getStartTime()).isEqualTo(date.withHour(11).withMinute(0));
			assertThat(selected.getEndTime()).isEqualTo(date.withHour(11).withMinute(30));
		}
	}

	@Test
	void shouldPreferAfternoonSlotWhenOwnerAsksForAfterLunch() throws Exception {
		Integer vetId = 1;
		LocalDateTime date = LocalDateTime.of(2026, 9, 1, 0, 0);

		// Vet availability: 08:00 - 17:00 (all free)
		VetAvailability availability = new VetAvailability(vetId, date.withHour(8).withMinute(0),
				date.withHour(17).withMinute(0));

		// A morning slot (earliest) and an afternoon slot are both available.
		CandidateSlot morningSlot = new CandidateSlot(vetId, date.withHour(8).withMinute(0), 30);
		CandidateSlot afternoonSlot = new CandidateSlot(vetId, date.withHour(14).withMinute(0), 30);
		List<CandidateSlot> candidateSlots = List.of(morningSlot,
				new CandidateSlot(vetId, date.withHour(9).withMinute(0), 30), afternoonSlot,
				new CandidateSlot(vetId, date.withHour(15).withMinute(30), 30));

		// Owner asked for "after lunch" -> afternoon (12:00 - 17:00).
		PreferredWindow afternoon = new PreferredWindow(null, null, LocalTime.NOON, LocalTime.of(17, 0));
		ProposedBooking booking = new ProposedBooking(100, 30, null, List.of(afternoon));

		AppointmentScheduleSolution problem = new AppointmentScheduleSolution(List.of(), List.of(availability),
				candidateSlots, booking);

		SolverConfig solverConfig = new SolverConfig().withSolutionClass(AppointmentScheduleSolution.class)
			.withEntityClasses(ProposedBooking.class)
			.withConstraintProviderClass(ScheduleConstraintProvider.class)
			.withTerminationConfig(new TerminationConfig().withSpentLimit(Duration.ofSeconds(1)));

		SolverFactory<AppointmentScheduleSolution> solverFactory = SolverFactory.create(solverConfig);
		try (SolverManager<AppointmentScheduleSolution, UUID> solverManager = SolverManager.create(solverFactory)) {
			UUID problemId = UUID.randomUUID();
			SolverJob<AppointmentScheduleSolution, UUID> solverJob = solverManager.solve(problemId, problem);

			AppointmentScheduleSolution solution = solverJob.getFinalBestSolution();

			assertThat(solution.getScore()).isNotNull();
			assertThat(solution.getScore().isFeasible()).isTrue();

			CandidateSlot selected = solution.getProposedBooking().getSelectedSlot();
			assertThat(selected).isNotNull();
			// The earliest slot is 08:00 (morning), but "after lunch" must win: the
			// solver
			// should pick the earliest afternoon slot (14:00), not the earlier morning
			// one.
			assertThat(selected.getStartTime().toLocalTime()).isEqualTo(LocalTime.of(14, 0));
		}
	}

	@Test
	void shouldOverrideInconsistentInterpretedDateWithNextMatchingWeekday() {
		// Today is Wednesday 2026-08-26. The owner asked for "Thursday" but the LLM
		// returned an inconsistent date (2026-08-26 is a Wednesday). The requested
		// weekday wins, so the date must be resolved to the next Thursday (2026-08-27).
		LocalDate today = LocalDate.of(2026, 8, 26);
		LocalDate inconsistent = LocalDate.of(2026, 8, 26);

		LocalDate resolved = AppointmentSolverService.resolveDate(DayOfWeek.THURSDAY, inconsistent, today);

		assertThat(resolved).isEqualTo(LocalDate.of(2026, 8, 27));
		assertThat(resolved.getDayOfWeek()).isEqualTo(DayOfWeek.THURSDAY);
	}

	@Test
	void shouldKeepInterpretedDateWhenConsistentWithWeekday() {
		LocalDate today = LocalDate.of(2026, 8, 26);
		LocalDate consistentThursday = LocalDate.of(2026, 9, 3);

		LocalDate resolved = AppointmentSolverService.resolveDate(DayOfWeek.THURSDAY, consistentThursday, today);

		assertThat(resolved).isEqualTo(consistentThursday);
	}

	@Test
	void shouldResolveWeekdayToNextOccurrenceWhenNoDateGiven() {
		LocalDate today = LocalDate.of(2026, 8, 26);

		LocalDate resolved = AppointmentSolverService.resolveDate(DayOfWeek.THURSDAY, null, today);

		assertThat(resolved).isEqualTo(LocalDate.of(2026, 8, 27));
	}

	@Test
	void shouldKeepDateWhenNoWeekdayGiven() {
		LocalDate today = LocalDate.of(2026, 8, 26);
		LocalDate onlyDate = LocalDate.of(2026, 8, 26);

		LocalDate resolved = AppointmentSolverService.resolveDate(null, onlyDate, today);

		assertThat(resolved).isEqualTo(onlyDate);
	}

}
