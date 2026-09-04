/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.architecture;

import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.architecture.fixtures.scheduling.request.ViolatingSchedulingService;
import org.springframework.samples.petclinic.architecture.fixtures.scheduling.solver.ViolatingSolverClass;
import org.springframework.samples.petclinic.architecture.fixtures.scheduling.solver.ViolatingSolutionSupport;
import org.springframework.samples.petclinic.architecture.fixtures.scheduling.web.ViolatingSchedulingController;
import org.springframework.stereotype.Controller;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture boundary tests enforcing RULE-2.
 */
class ArchitectureBoundaryTests {

	/**
	 * RULE-2's adapter boundary includes these exact implementation/support types. The
	 * three Timefold support types are required by RULE-26; the ranker and interpreter
	 * implementations are the framework-facing adapters. Keeping this list exact avoids
	 * granting framework access merely because a class name contains words such as
	 * "Solution" or "Assignment".
	 */
	private static final Set<String> FRAMEWORK_ADAPTER_BOUNDARY = Set.of(
			"org.springframework.samples.petclinic.scheduling.solver.AppointmentAssignment",
			"org.springframework.samples.petclinic.scheduling.solver.ScheduleSolution",
			"org.springframework.samples.petclinic.scheduling.solver.AppointmentConstraintProvider",
			"org.springframework.samples.petclinic.scheduling.solver.DefaultSlotRanker",
			"org.springframework.samples.petclinic.scheduling.interpretation.OllamaRequestInterpreter");

	private static JavaClasses productionClasses;

	@BeforeAll
	static void importProductionClasses() {
		productionClasses = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("org.springframework.samples.petclinic");
	}

	static final ArchRule RULE_NO_AI_OR_TIMEFOLD_OUTSIDE_ADAPTERS = noClasses().that()
		.resideInAnyPackage("..scheduling.solver..", "..scheduling.interpretation..")
		.and(new DescribedPredicate<JavaClass>("outside the exact framework adapter boundary") {
			@Override
			public boolean test(JavaClass javaClass) {
				return !FRAMEWORK_ADAPTER_BOUNDARY.contains(javaClass.getName());
			}
		})
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("ai.timefold..", "org.springframework.ai..")
		.allowEmptyShould(true);

	static final ArchRule RULE_NO_CONTROLLER_DIRECT_REPOSITORY_ACCESS = noClasses().that()
		.areAnnotatedWith(Controller.class)
		.and()
		.resideInAPackage("..scheduling..")
		.should()
		.dependOnClassesThat()
		.haveSimpleNameEndingWith("Repository")
		.allowEmptyShould(true);

	static final ArchRule RULE_NO_SERVLET_OR_WEB_IN_SCHEDULING_SERVICE_OR_DOMAIN = noClasses().that()
		.resideInAnyPackage("..scheduling.request..", "..scheduling.interpretation..", "..scheduling.solver..",
				"..scheduling.appointment..", "..scheduling.clinic..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("jakarta.servlet..", "org.springframework.web..")
		.allowEmptyShould(true);

	@Test
	void productionCodeSatisfiesNoAiOrTimefoldOutsideAdapters() {
		RULE_NO_AI_OR_TIMEFOLD_OUTSIDE_ADAPTERS.check(productionClasses);
	}

	@Test
	void productionCodeSatisfiesNoControllerDirectRepositoryAccess() {
		RULE_NO_CONTROLLER_DIRECT_REPOSITORY_ACCESS.check(productionClasses);
	}

	@Test
	void productionCodeSatisfiesNoServletOrWebInSchedulingServiceOrDomain() {
		RULE_NO_SERVLET_OR_WEB_IN_SCHEDULING_SERVICE_OR_DOMAIN.check(productionClasses);
	}

	@Test
	void ruleNoAiOrTimefoldOutsideAdaptersFailsOnViolatingFixture() {
		JavaClasses violating = new ClassFileImporter().importClasses(ViolatingSolverClass.class,
				ViolatingSolutionSupport.class);
		EvaluationResult result = RULE_NO_AI_OR_TIMEFOLD_OUTSIDE_ADAPTERS.evaluate(violating);
		assertThat(result.hasViolation()).isTrue();
		assertThat(result.getFailureReport().getDetails())
			.anyMatch(detail -> detail.contains("ViolatingSolutionSupport"));
	}

	@Test
	void ruleNoControllerDirectRepositoryAccessFailsOnViolatingFixture() {
		JavaClasses violating = new ClassFileImporter().importClasses(ViolatingSchedulingController.class);
		EvaluationResult result = RULE_NO_CONTROLLER_DIRECT_REPOSITORY_ACCESS.evaluate(violating);
		assertThat(result.hasViolation()).isTrue();
	}

	@Test
	void ruleNoServletOrWebInSchedulingServiceOrDomainFailsOnViolatingFixture() {
		JavaClasses violating = new ClassFileImporter().importClasses(ViolatingSchedulingService.class);
		EvaluationResult result = RULE_NO_SERVLET_OR_WEB_IN_SCHEDULING_SERVICE_OR_DOMAIN.evaluate(violating);
		assertThat(result.hasViolation()).isTrue();
	}

}
