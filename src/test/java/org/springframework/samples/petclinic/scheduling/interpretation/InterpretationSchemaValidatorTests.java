package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretationSchemaValidatorTests {

	private final InterpretationSchemaValidator validator = new InterpretationSchemaValidator();

	private final ClinicVocabulary vocabulary = new ClinicVocabulary(Set.of(15, 30, 45), Set.of("dentistry"),
			Set.of("carter"), Map.of("dentistry", 1), Map.of("carter", 2), "America/Chicago");

	@Test
	void missingRequiredFieldIsInvalid() {
		InterpretationValidationResult result = this.validator.validate("{\"schemaVersion\":\"1.0\"}", this.vocabulary);
		assertThat(result.classification()).isEqualTo(InterpretationClassification.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void unknownFieldsAreCapturedAndRemoved() {
		String json = validJson().replace("\"uncertainties\": []",
				"\"uncertainties\": [], \"confidence\": 0.9, \"extra\": true");
		InterpretationValidationResult result = this.validator.validate(json, this.vocabulary);
		assertThat(result.unknownFields()).containsKeys("/confidence", "/extra");
		assertThat(result.recognizedJson()).doesNotContain("confidence");
		assertThat(result.classification()).isEqualTo(InterpretationClassification.VALID_REVIEWABLE);
	}

	@Test
	void invalidDurationIsRejected() {
		String json = validJson().replace("\"durationMinutes\": 30", "\"durationMinutes\": 7");
		InterpretationValidationResult result = this.validator.validate(json, this.vocabulary);
		assertThat(result.issueCodes()).contains("INVALID_DURATION");
		assertThat(result.classification()).isEqualTo(InterpretationClassification.INVALID_STRUCTURED_OUTPUT);
	}

	@Test
	void uncertaintyIsValidButNeedsStaffAndIsNotRetriedByValidator() {
		String json = validJson().replace("\"uncertainties\": []",
				"\"uncertainties\": [{\"fieldPath\":\"/visitReason\",\"code\":\"MISSING_INFORMATION\"}]");
		InterpretationValidationResult result = this.validator.validate(json, this.vocabulary);
		assertThat(result.classification()).isEqualTo(InterpretationClassification.VALID_NEEDS_STAFF);
	}

	@Test
	void explicitNullsAreAcceptedForOptionalCodes() {
		InterpretationValidationResult result = this.validator.validate(validJson(), this.vocabulary);
		assertThat(result.recognized().requiredSpecialtyCode()).isNull();
		assertThat(result.recognized().preferredVeterinarianCode()).isNull();
	}

	private String validJson() {
		return """
				{
				  "schemaVersion": "1.0",
				  "visitReason": "Annual checkup",
				  "durationMinutes": 30,
				  "careType": "GENERAL",
				  "requiredSpecialtyCode": null,
				  "allowedWindows": [{
				    "sourcePhrase": "next week mornings",
				    "resolvedStart": "2026-09-07T08:00:00-05:00",
				    "resolvedEnd": "2026-09-11T12:00:00-05:00",
				    "resolution": "RESOLVED",
				    "fallbackAllowed": false
				  }],
				  "preferredWindows": [],
				  "excludedWindows": [],
				  "preferredVeterinarianCode": null,
				  "veterinarianPreferenceStrength": "NONE",
				  "urgency": "NO_CONCERN_IDENTIFIED",
				  "unresolvedDates": [],
				  "uncertainties": []
				}
				""";
	}

}
