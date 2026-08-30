package org.springframework.samples.petclinic.scheduling.architecture;

import org.springframework.samples.petclinic.scheduling.appointment.ReservationService;
import org.springframework.samples.petclinic.scheduling.matching.TimefoldSlotSolver;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "org.springframework.samples.petclinic",
		importOptions = ImportOption.DoNotIncludeTests.class)
class SchedulingArchitectureTests {

	@ArchTest
	static final ArchRule webMustNotSkipToReservationOrIntegrationRepositories = noClasses().that()
		.resideInAPackage("..scheduling.web..")
		.should()
		.dependOnClassesThat()
		.haveNameMatching(".*(ReservationBlock|Hold|Offer|Appointment|IntegrationExecution)Repository")
		.because("web packages must not skip to reservation or integration persistence");

	@ArchTest
	static final ArchRule ownerWebMustNotSelectSlotsOutsideTimefoldPort = noClasses().that()
		.resideInAPackage("..scheduling.web.owner..")
		.should()
		.dependOnClassesThat()
		.areAssignableTo(TimefoldSlotSolver.class)
		.orShould()
		.dependOnClassesThat()
		.areAssignableTo(ReservationService.class)
		.because("owner-facing slot selection must go through the Timefold matching port");

	@ArchTest
	static final ArchRule timefoldSolverStaysInsideMatchingPort = noClasses().that()
		.resideOutsideOfPackage("..scheduling.matching..")
		.should()
		.dependOnClassesThat()
		.areAssignableTo(TimefoldSlotSolver.class)
		.because("automated owner slot selection must cross the Timefold matching port");

}
