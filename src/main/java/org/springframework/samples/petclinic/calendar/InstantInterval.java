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
package org.springframework.samples.petclinic.calendar;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record InstantInterval(Instant start, Instant end) implements Comparable<InstantInterval>, Serializable {

	public InstantInterval {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("start (" + start + ") must be before end (" + end + ")");
		}
	}

	public Duration duration() {
		return Duration.between(this.start, this.end);
	}

	public long durationMinutes() {
		return duration().toMinutes();
	}

	public boolean contains(Instant instant) {
		return instant != null && !instant.isBefore(this.start) && instant.isBefore(this.end);
	}

	public boolean covers(Instant otherStart, Instant otherEnd) {
		if (otherStart == null || otherEnd == null) {
			return false;
		}
		return !otherStart.isBefore(this.start) && !otherEnd.isAfter(this.end);
	}

	public boolean overlaps(InstantInterval other) {
		if (other == null) {
			return false;
		}
		return this.start.isBefore(other.end()) && other.start().isBefore(this.end);
	}

	@Override
	public int compareTo(InstantInterval o) {
		if (o == null) {
			return 1;
		}
		int startComp = this.start.compareTo(o.start());
		return startComp != 0 ? startComp : this.end.compareTo(o.end());
	}

}
