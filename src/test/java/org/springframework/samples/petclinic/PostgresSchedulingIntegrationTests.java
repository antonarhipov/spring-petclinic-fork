package org.springframework.samples.petclinic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.samples.petclinic.scheduling.offer.ReservationBlock;
import org.springframework.samples.petclinic.scheduling.offer.ReservationBlockRepository;
import org.springframework.samples.petclinic.scheduling.offer.ReservationState;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("postgres")
@Testcontainers(disabledWithoutDocker = true)
class PostgresSchedulingIntegrationTests {

	@ServiceConnection
	@Container
	static PostgreSQLContainer container = new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));

	@Autowired
	private ReservationBlockRepository blocks;

	@Test
	void migrationEnforcesUniqueVeterinarianReservationBlocks() {
		Instant slot = Instant.parse("2030-01-02T09:00:00Z");
		this.blocks.saveAndFlush(new ReservationBlock("VETERINARIAN", 996, slot, "OFFER", 1, ReservationState.HELD,
				slot.plusSeconds(600)));
		assertThatThrownBy(() -> this.blocks.saveAndFlush(new ReservationBlock("VETERINARIAN", 996, slot, "OFFER", 2,
				ReservationState.HELD, slot.plusSeconds(600))))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
