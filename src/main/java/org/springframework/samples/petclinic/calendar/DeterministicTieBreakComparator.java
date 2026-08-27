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
import java.util.Comparator;

public class DeterministicTieBreakComparator implements Comparator<SlotCandidate>, Serializable {

	public static final DeterministicTieBreakComparator INSTANCE = new DeterministicTieBreakComparator();

	@Override
	public int compare(SlotCandidate c1, SlotCandidate c2) {
		if (c1 == c2) {
			return 0;
		}
		if (c1 == null) {
			return 1;
		}
		if (c2 == null) {
			return -1;
		}

		int vetComp = Integer.compare(c1.vetId() != null ? c1.vetId() : 0, c2.vetId() != null ? c2.vetId() : 0);
		if (vetComp != 0) {
			return vetComp;
		}

		if (c1.startInstant() == null && c2.startInstant() == null) {
			return 0;
		}
		if (c1.startInstant() == null) {
			return 1;
		}
		if (c2.startInstant() == null) {
			return -1;
		}

		return c1.startInstant().compareTo(c2.startInstant());
	}

}
