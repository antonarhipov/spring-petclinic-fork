package org.springframework.samples.petclinic.scheduling.offer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class ReservationBlockRepositoryTests {

	@Autowired
	private ReservationBlockRepository blocks;

	@Test
	void databaseRejectsTwoClaimsForOneResourceGridSlot() {
		Instant slot = Instant.parse("2030-01-01T09:00:00Z");
		this.blocks.saveAndFlush(new ReservationBlock("VETERINARIAN", 999, slot, "OFFER", 1, ReservationState.HELD,
				slot.plusSeconds(600)));
		assertThatThrownBy(() -> this.blocks.saveAndFlush(new ReservationBlock("VETERINARIAN", 999, slot, "OFFER", 2,
				ReservationState.HELD, slot.plusSeconds(600))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
