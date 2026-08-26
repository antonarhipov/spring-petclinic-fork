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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A solver-friendly representation of a time window the owner prefers, derived from the
 * AI {@code SymbolicWindow}. Any component may be {@code null}, meaning "no constraint on
 * that dimension". The part-of-day is expressed as an explicit time range so the solver
 * can reason about it directly.
 */
public record PreferredWindow(DayOfWeek dayOfWeek, LocalDate date, LocalTime startInclusive, LocalTime endExclusive) {

	/**
	 * Returns {@code true} when the given candidate slot falls inside this window. A
	 * {@code null} component is treated as "matches anything" for that dimension.
	 */
	public boolean matches(CandidateSlot slot) {
		if (slot == null) {
			return false;
		}
		LocalDateTime start = slot.getStartTime();
		if (this.date != null && !start.toLocalDate().equals(this.date)) {
			return false;
		}
		if (this.dayOfWeek != null && start.getDayOfWeek() != this.dayOfWeek) {
			return false;
		}
		if (this.startInclusive != null) {
			LocalTime time = start.toLocalTime();
			if (time.isBefore(this.startInclusive)) {
				return false;
			}
			if (this.endExclusive != null && !time.isBefore(this.endExclusive)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A window carries no usable constraint when every dimension is {@code null}.
	 */
	public boolean isEmpty() {
		return this.dayOfWeek == null && this.date == null && this.startInclusive == null;
	}

}
