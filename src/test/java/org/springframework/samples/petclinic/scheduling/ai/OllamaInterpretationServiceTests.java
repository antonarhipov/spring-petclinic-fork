package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

class OllamaInterpretationServiceTests {

	private final InterpretationValidator validator = new InterpretationValidator();

	@Test
	void acceptsConfiguredStructuredResponse() {
		assertThatCode(() -> this.validator.validate(new InterpretationResponse("Runny nose", 30, "GENERAL", null,
				"STANDARD", null, List.of(new InterpretationAvailabilityWindow(null, 2, "13:00", "17:00")))))
			.doesNotThrowAnyException();
	}

	@Test
	void promptDefinesRunnyNoseAsStandardGeneralCare() {
		assertThat(OllamaInterpretationService.SYSTEM_PROMPT).contains(
				"leo has running nose\nplease schedule the visit for next thursday after lunch",
				"An unknown diagnosis does not make careType uncertain", "runny nose", "careType=\"GENERAL\"",
				"urgency=\"STANDARD\"", "dayOfWeek=4", "startTime=\"13:00\"", "endTime=\"17:00\"",
				"Omit requiredSpecialty and preferredVeterinarian");
	}

	@Test
	void optionalFieldsAreNotRequiredByTheStructuredOutputSchema() throws Exception {
		String schema = JsonSchemaGenerator.generateForType(InterpretationResponse.class);
		String requiredProperties = JacksonUtils.getDefaultJsonMapper().readTree(schema).get("required").toString();

		assertThat(requiredProperties).contains("visitReason", "durationMinutes", "careType", "urgency")
			.doesNotContain("requiredSpecialty", "preferredVeterinarian", "preferredWindows");
	}

	@Test
	void normalizesTextualNullOptionalFields() {
		InterpretationResponse response = new InterpretationResponse("Runny nose", 30, "GENERAL", "null", "STANDARD",
				" null ", null);

		assertThat(response.requiredSpecialty()).isNull();
		assertThat(response.preferredVeterinarian()).isNull();
		assertThat(response.preferredWindows()).isEmpty();
	}

	@Test
	void routesUncertainOrUnsupportedInterpretationsToValidationFailure() {
		assertThatThrownBy(() -> this.validator
			.validate(new InterpretationResponse("Limping", 20, "GENERAL", null, "STANDARD", null, null)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.validator
			.validate(new InterpretationResponse("Limping", 30, "UNCERTAIN", null, "STANDARD", null, null)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMalformedPreferredWindows() {
		assertThatThrownBy(() -> this.validator.validate(new InterpretationResponse("Checkup", 30, "GENERAL", null,
				"STANDARD", null, List.of(new InterpretationAvailabilityWindow(null, 2, "13:10", "12:00")))))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
