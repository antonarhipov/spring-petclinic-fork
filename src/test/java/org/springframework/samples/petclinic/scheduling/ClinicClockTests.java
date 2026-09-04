/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClinicClockTests {

	@Autowired
	private Clock clock;

	@Test
	void runtimeClockUsesTheSeededClinicTimeZone() {
		assertThat(this.clock.getZone()).isEqualTo(ZoneId.of("Europe/Amsterdam"));
	}

	@Test
	void productionCodeHasNoDirectSystemNowCalls() throws IOException {
		List<String> violations;
		try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
			violations = paths.filter(path -> path.toString().endsWith(".java"))
				.flatMap(path -> lines(path)
					.filter(line -> line
						.matches(".*(?:LocalDate|LocalDateTime|ZonedDateTime|Instant|OffsetDateTime)\\.now\\(\\).*"))
					.map(line -> path + ": " + line.trim()))
				.toList();
		}
		assertThat(violations).isEmpty();
	}

	private static Stream<String> lines(Path path) {
		try {
			return Files.readAllLines(path).stream();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
