package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OllamaInterpretationServiceTests {

	private final InterpretationValidator validator = new InterpretationValidator();

	@Test
	void acceptsConfiguredStructuredResponse() {
		assertThatCode(() -> this.validator
			.validate(new InterpretationResponse("Limping", 30, "GENERAL", null, "STANDARD", null)))
			.doesNotThrowAnyException();
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
