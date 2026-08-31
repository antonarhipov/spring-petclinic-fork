package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.scheduling.request.WindowClassification;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretationValidationTests {

	private InterpretationOutputValidator validator;

	private ClinicPolicy policy;

	@BeforeEach
	void setUp() {
		this.validator = new InterpretationOutputValidator();
		this.policy = ClinicPolicy.createDefaultPolicy();
	}

	@Test
	void validCandidatePassesValidation() {
		WindowCandidate window = new WindowCandidate(WindowClassification.PREFERRED, WindowShape.ONE_OFF,
				LocalDate.of(2026, 9, 1), null, null, null, LocalTime.of(9, 0), LocalTime.of(12, 0), "Tuesday morning",
				null);

		InterpretationCandidate candidate = new InterpretationCandidate("1", "Annual vaccination and health checkup",
				Urgency.ROUTINE, List.of(), 30, new CatalogChoice(1, "Dr. Carter"), null, List.of(window), List.of(),
				List.of());

		InterpretationOutputValidator.ValidationResult result = this.validator.validate(candidate, this.policy);

		assertThat(result.valid()).isTrue();
		assertThat(result.errors()).isEmpty();
	}

	@Test
	void invalidSchemaVersionFailsValidation() {
		InterpretationCandidate candidate = new InterpretationCandidate("2", "Checkup", Urgency.ROUTINE, List.of(), 30,
				null, null, List.of(), List.of(), List.of());

		InterpretationOutputValidator.ValidationResult result = this.validator.validate(candidate, this.policy);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).anyMatch(err -> err.contains("Invalid schemaVersion"));
	}

	@Test
	void blankVisitReasonFailsValidation() {
		InterpretationCandidate candidate = new InterpretationCandidate("1", "   ", Urgency.ROUTINE, List.of(), 30,
				null, null, List.of(), List.of(), List.of());

		InterpretationOutputValidator.ValidationResult result = this.validator.validate(candidate, this.policy);

		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).anyMatch(err -> err.contains("visitReason must not be blank"));
	}

	@Test
	void disallowedDurationFailsValidation() {
		// 25 is not a multiple of 15
		InterpretationCandidate nonMultiple = new InterpretationCandidate("1", "Checkup", Urgency.ROUTINE, List.of(),
				25, null, null, List.of(), List.of(), List.of());
		assertThat(this.validator.validate(nonMultiple, this.policy).valid()).isFalse();

		// 75 is a multiple of 15, but not in standard policy (15, 30, 45, 60, 90, 120)
		InterpretationCandidate disallowedByPolicy = new InterpretationCandidate("1", "Checkup", Urgency.ROUTINE,
				List.of(), 75, null, null, List.of(), List.of(), List.of());
		InterpretationOutputValidator.ValidationResult result = this.validator.validate(disallowedByPolicy,
				this.policy);
		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).anyMatch(err -> err.contains("not allowed by clinic policy"));
	}

	@Test
	void invalidWindowIntervalFailsValidation() {
		// End time before start time
		WindowCandidate invalidTimeWindow = new WindowCandidate(WindowClassification.ALLOWED, WindowShape.ONE_OFF,
				LocalDate.of(2026, 9, 1), null, null, null, LocalTime.of(14, 0), LocalTime.of(10, 0), "Afternoon",
				null);

		InterpretationCandidate candidate = new InterpretationCandidate("1", "Checkup", Urgency.ROUTINE, List.of(), 30,
				null, null, List.of(invalidTimeWindow), List.of(), List.of());

		InterpretationOutputValidator.ValidationResult result = this.validator.validate(candidate, this.policy);
		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).anyMatch(err -> err.contains("startTime must be before endTime"));
	}

	@Test
	void weeklyWindowMissingDaysFailsValidation() {
		WindowCandidate weeklyNoDays = new WindowCandidate(WindowClassification.PREFERRED, WindowShape.WEEKLY, null,
				LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), List.of(), LocalTime.of(9, 0), LocalTime.of(12, 0),
				"Weekday mornings", null);

		InterpretationCandidate candidate = new InterpretationCandidate("1", "Checkup", Urgency.ROUTINE, List.of(), 30,
				null, null, List.of(weeklyNoDays), List.of(), List.of());

		InterpretationOutputValidator.ValidationResult result = this.validator.validate(candidate, this.policy);
		assertThat(result.valid()).isFalse();
		assertThat(result.errors()).anyMatch(err -> err.contains("WEEKLY window requires at least one weekday"));
	}

	@Test
	void emergencyKeywordScreenDetectsEmergencyPhrases() {
		EmergencyKeywordScreen screen = new EmergencyKeywordScreen();

		assertThat(screen.screen("My dog is bleeding heavily from the paw").emergencyDetected()).isTrue();
		assertThat(screen.screen("Cat ingested toxic plant and cannot breathe").emergencyDetected()).isTrue();
		assertThat(screen.screen("Pet is having seizures").emergencyDetected()).isTrue();

		// Routine cases
		assertThat(screen.screen("Routine checkup for rabies vaccine next week").emergencyDetected()).isFalse();
		assertThat(screen.screen("Ear cleaning and nail trim").emergencyDetected()).isFalse();
	}

}
