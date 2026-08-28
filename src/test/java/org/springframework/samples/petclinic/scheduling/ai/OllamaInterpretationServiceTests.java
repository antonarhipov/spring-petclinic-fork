package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

class OllamaInterpretationServiceTests {

	private final InterpretationValidator validator = new InterpretationValidator();

	@Test
	void acceptsConfiguredStructuredResponse() {
		assertThatCode(() -> this.validator
			.validate(new InterpretationResponse("Runny nose", 30, "GENERAL", null, "STANDARD", null)))
			.doesNotThrowAnyException();
	}

	@Test
	void promptDefinesRunnyNoseAsStandardGeneralCare() {
		assertThat(OllamaInterpretationService.SYSTEM_PROMPT).contains(
				"leo has running nose\nplease schedule the visit for next thursday after lunch",
				"An unknown diagnosis does not make careType uncertain", "runny nose", "careType=\"GENERAL\"",
				"urgency=\"STANDARD\"", "Omit requiredSpecialty and preferredVeterinarian");
	}

	@Test
	void optionalFieldsAreNotRequiredByTheStructuredOutputSchema() throws Exception {
		String schema = JsonSchemaGenerator.generateForType(InterpretationResponse.class);
		String requiredProperties = JacksonUtils.getDefaultJsonMapper().readTree(schema).get("required").toString();

		assertThat(requiredProperties).contains("visitReason", "durationMinutes", "careType", "urgency")
			.doesNotContain("requiredSpecialty", "preferredVeterinarian");
	}

	@Test
	void normalizesTextualNullOptionalFields() {
		InterpretationResponse response = new InterpretationResponse("Runny nose", 30, "GENERAL", "null", "STANDARD",
				" null ");

		assertThat(response.requiredSpecialty()).isNull();
		assertThat(response.preferredVeterinarian()).isNull();
	}

	@Test
	void routesUncertainOrUnsupportedInterpretationsToValidationFailure() {
		assertThatThrownBy(() -> this.validator
			.validate(new InterpretationResponse("Limping", 20, "GENERAL", null, "STANDARD", null)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.validator
			.validate(new InterpretationResponse("Limping", 30, "UNCERTAIN", null, "STANDARD", null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
