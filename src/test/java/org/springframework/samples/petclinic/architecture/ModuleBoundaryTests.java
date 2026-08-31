package org.springframework.samples.petclinic.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Phase 1/2 package boundary guards for the security and shared foundation.
 * <p>
 * Package patterns are fully qualified so JDK types such as {@code java.security.*} are
 * not mistaken for application {@code ..security..} packages.
 */
@AnalyzeClasses(packages = "org.springframework.samples.petclinic",
		importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTests {

	@ArchTest
	static final ArchRule sharedMustNotDependOnFeaturePackages = noClasses().that()
		.resideInAPackage("org.springframework.samples.petclinic.shared..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.samples.petclinic.owner..",
				"org.springframework.samples.petclinic.vet..", "org.springframework.samples.petclinic.security..",
				"org.springframework.samples.petclinic.account..", "org.springframework.samples.petclinic.audit..")
		.because("shared primitives must remain feature-agnostic");

	@ArchTest
	static final ArchRule accountMustNotDependOnOwnerOrVetPackages = noClasses().that()
		.resideInAPackage("org.springframework.samples.petclinic.account..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.samples.petclinic.owner..",
				"org.springframework.samples.petclinic.vet..", "org.springframework.samples.petclinic.system..")
		.because("account identity loading stays independent of catalog/UI packages");

	@ArchTest
	static final ArchRule auditMustNotDependOnWebOrSecurityPackages = noClasses().that()
		.resideInAPackage("org.springframework.samples.petclinic.audit..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.samples.petclinic.security..",
				"org.springframework.samples.petclinic.system..", "org.springframework.samples.petclinic.owner..",
				"org.springframework.samples.petclinic.vet..")
		.because("encryption and audit persistence must not couple to web controllers");

	@ArchTest
	static final ArchRule securityMustNotDependOnVetPackage = noClasses().that()
		.resideInAPackage("org.springframework.samples.petclinic.security..")
		.should()
		.dependOnClassesThat()
		.resideInAPackage("org.springframework.samples.petclinic.vet..")
		.because("security infrastructure is independent of veterinarian catalog");

	@ArchTest
	static final ArchRule systemWebMustNotDependOnAuditPersistence = noClasses().that()
		.resideInAPackage("org.springframework.samples.petclinic.system..")
		.should()
		.dependOnClassesThat()
		.resideInAnyPackage("org.springframework.samples.petclinic.audit..",
				"org.springframework.samples.petclinic.shared.command..")
		.because("system web adapters stay free of audit/command persistence details");

}
