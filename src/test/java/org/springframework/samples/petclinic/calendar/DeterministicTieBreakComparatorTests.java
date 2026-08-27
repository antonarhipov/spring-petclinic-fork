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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicTieBreakComparatorTests {

	private final DeterministicTieBreakComparator comparator = DeterministicTieBreakComparator.INSTANCE;

	@Test
	void breaksTieByAscendingVetIdWhenStartInstantsAreEqual() {
		Instant now = Instant.parse("2026-06-01T10:00:00Z");
		SlotCandidate vet1 = new SlotCandidate(1, now);
		SlotCandidate vet2 = new SlotCandidate(2, now);

		assertThat(this.comparator.compare(vet1, vet2)).isNegative();
		assertThat(this.comparator.compare(vet2, vet1)).isPositive();
	}

	@Test
	void breaksTieByAscendingStartInstantWhenVetIdsAreEqual() {
		Instant t1 = Instant.parse("2026-06-01T09:00:00Z");
		Instant t2 = Instant.parse("2026-06-01T10:00:00Z");
		SlotCandidate slot1 = new SlotCandidate(1, t1);
		SlotCandidate slot2 = new SlotCandidate(1, t2);

		assertThat(this.comparator.compare(slot1, slot2)).isNegative();
		assertThat(this.comparator.compare(slot2, slot1)).isPositive();
	}

	@Test
	void vetIdTakesPrecedenceOverStartInstant() {
		Instant earlier = Instant.parse("2026-06-01T08:00:00Z");
		Instant later = Instant.parse("2026-06-01T12:00:00Z");

		SlotCandidate vet1Later = new SlotCandidate(1, later);
		SlotCandidate vet2Earlier = new SlotCandidate(2, earlier);

		// Vet 1 wins despite having a later start instant
		assertThat(this.comparator.compare(vet1Later, vet2Earlier)).isNegative();
		assertThat(this.comparator.compare(vet2Earlier, vet1Later)).isPositive();
	}

	@Test
	void equalCandidatesReturnZero() {
		Instant t = Instant.parse("2026-06-01T10:00:00Z");
		SlotCandidate c1 = new SlotCandidate(3, t);
		SlotCandidate c2 = new SlotCandidate(3, t);

		assertThat(this.comparator.compare(c1, c2)).isZero();
		assertThat(this.comparator.compare(c1, c1)).isZero();
	}

	@Test
	void sortingIsStrictlyReproducibleAcrossMultipleRuns() {
		Instant base = Instant.parse("2026-06-01T08:00:00Z");
		List<SlotCandidate> pool = new ArrayList<>();
		for (int v = 1; v <= 5; v++) {
			for (int h = 0; h < 10; h++) {
				pool.add(new SlotCandidate(v, base.plusSeconds(h * 900L)));
			}
		}

		List<SlotCandidate> run1 = new ArrayList<>(pool);
		Collections.shuffle(run1, new Random(42));
		run1.sort(this.comparator);

		List<SlotCandidate> run2 = new ArrayList<>(pool);
		Collections.shuffle(run2, new Random(1337));
		run2.sort(this.comparator);

		assertThat(run1).isEqualTo(run2);

		// Verify first element is vet 1 with earliest start
		assertThat(run1.get(0)).isEqualTo(new SlotCandidate(1, base));
		// Verify last element is vet 5 with latest start
		assertThat(run1.get(run1.size() - 1)).isEqualTo(new SlotCandidate(5, base.plusSeconds(9 * 900L)));
	}

}
