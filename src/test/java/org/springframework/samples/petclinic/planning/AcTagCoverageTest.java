/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcTagCoverageTest {

	@Test
	void everyCompleteTaskAcHasTestCitation() throws IOException {
		PlanGuardSupport.Plan plan = PlanGuardSupport.readPlan();
		Set<String> cited = PlanGuardSupport.citedAcceptanceCriteria(allTestSourceLines());
		for (PlanGuardSupport.TaskSpec task : plan.tasks().values()) {
			if (task.complete()) {
				assertCited(task.acs(), cited);
			}
		}
	}

	@Test
	void untaggedAcFixtureFails() {
		Set<String> cited = PlanGuardSupport
			.citedAcceptanceCriteria(List.of("@Test", "void evidenceWithoutAnAcceptanceCriterion() {}"));
		assertThatThrownBy(() -> assertCited(Set.of("AC-999"), cited)).isInstanceOf(AssertionError.class)
			.hasMessageContaining("AC-999");
	}

	private static List<String> allTestSourceLines() throws IOException {
		List<String> lines = new ArrayList<>();
		try (var paths = Files.walk(Path.of("src/test/java"))) {
			for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
				lines.addAll(Files.readAllLines(path));
			}
		}
		return lines;
	}

	private static void assertCited(Set<String> required, Set<String> cited) {
		for (String ac : required) {
			if (!cited.contains(ac)) {
				throw new AssertionError(ac + " has no @Tag, @DisplayName, or method-name citation");
			}
		}
	}

}
