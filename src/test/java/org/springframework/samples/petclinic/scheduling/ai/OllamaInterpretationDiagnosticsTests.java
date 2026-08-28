package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OllamaInterpretationDiagnosticsTests {

	@Test
	void failuresHaveNoOwnerTextPayload() {
		InterpretationPort.InterpretationResult failure = InterpretationPort.InterpretationResult
			.failed(InterpretationFailure.UNAVAILABLE);
		assertThat(failure.response()).isNull();
		assertThat(failure.failure()).isEqualTo(InterpretationFailure.UNAVAILABLE);
	}

}
