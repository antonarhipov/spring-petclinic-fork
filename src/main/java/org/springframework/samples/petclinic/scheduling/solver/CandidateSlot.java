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

import java.time.LocalDateTime;
import java.util.Objects;

public class CandidateSlot {

	private final Integer vetId;

	private final LocalDateTime startTime;

	private final LocalDateTime endTime;

	public CandidateSlot(Integer vetId, LocalDateTime startTime, int durationMinutes) {
		this.vetId = vetId;
		this.startTime = startTime;
		this.endTime = startTime.plusMinutes(durationMinutes);
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public LocalDateTime getStartTime() {
		return this.startTime;
	}

	public LocalDateTime getEndTime() {
		return this.endTime;
	}

	public int getDurationMinutes() {
		return (int) java.time.Duration.between(this.startTime, this.endTime).toMinutes();
	}

	public boolean overlapsWith(LocalDateTime start, LocalDateTime end) {
		return this.startTime.isBefore(end) && this.endTime.isAfter(start);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		CandidateSlot that = (CandidateSlot) o;
		return Objects.equals(this.vetId, that.vetId) && Objects.equals(this.startTime, that.startTime)
				&& Objects.equals(this.endTime, that.endTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.vetId, this.startTime, this.endTime);
	}

	@Override
	public String toString() {
		return "CandidateSlot{vetId=" + this.vetId + ", start=" + this.startTime + ", end=" + this.endTime + "}";
	}

}
