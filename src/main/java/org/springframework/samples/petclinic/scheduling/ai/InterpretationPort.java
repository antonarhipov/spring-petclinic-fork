package org.springframework.samples.petclinic.scheduling.ai;

public interface InterpretationPort {

	InterpretationResult interpret(String sourceText, String correlationId);

	record InterpretationResult(InterpretationResponse response, InterpretationFailure failure) {

		public boolean isSuccessful() {
			return this.response != null && this.failure == null;
		}

		public static InterpretationResult success(InterpretationResponse response) {
			return new InterpretationResult(response, null);
		}

		public static InterpretationResult failed(InterpretationFailure failure) {
			return new InterpretationResult(null, failure);
		}

	}

}
