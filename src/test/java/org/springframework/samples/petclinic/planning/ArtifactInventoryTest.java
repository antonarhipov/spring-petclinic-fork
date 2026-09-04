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
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactInventoryTest {

	@Test
	void everyCompleteTaskArtifactExistsIncludingAsBuiltPhase() throws IOException {
		PlanGuardSupport.Plan plan = PlanGuardSupport.readPlan();
		assertArtifacts(plan, plan.completed());
	}

	@Test
	void missingArtifactFixtureFails() {
		PlanGuardSupport.TaskSpec fixture = new PlanGuardSupport.TaskSpec("task-0.1",
				List.of("src/main/java/definitely-missing.java"), Set.of(), true);
		PlanGuardSupport.Plan broken = new PlanGuardSupport.Plan(java.util.Map.of(fixture.id(), fixture), List.of(),
				Set.of(fixture.id()));

		assertThatThrownBy(() -> assertArtifacts(broken, broken.completed())).isInstanceOf(AssertionError.class)
			.hasMessageContaining("src/main/java/definitely-missing.java");
		assertThatCode(() -> assertArtifacts(broken, Set.of())).doesNotThrowAnyException();
	}

	private static void assertArtifacts(PlanGuardSupport.Plan plan, Set<String> completed) {
		for (PlanGuardSupport.TaskSpec task : plan.tasks().values()) {
			if (!completed.contains(task.id())) {
				continue;
			}
			for (String declaration : task.artifacts()) {
				String path = PlanGuardSupport.artifactPath(declaration);
				String deletionTask = PlanGuardSupport.deletionTask(declaration);
				boolean shouldExist = deletionTask == null || !completed.contains(deletionTask);
				if (shouldExist != Files.exists(Path.of(path))) {
					throw new AssertionError(
							path + (shouldExist ? " must exist" : " must be absent after " + deletionTask));
				}
			}
		}
	}

}
