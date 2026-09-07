package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.concurrent.CompletionStage;

public interface Interpreter {

	CompletionStage<InterpretationResult> interpret(String prompt);

	final class InterpretationException extends RuntimeException {

		private final FailureKind kind;

		private final String rawOutput;

		public InterpretationException(FailureKind kind, String rawOutput, Throwable cause) {
			super(kind.name(), cause);
			this.kind = kind;
			this.rawOutput = rawOutput;
		}

		public FailureKind getKind() {
			return this.kind;
		}

		public String getRawOutput() {
			return this.rawOutput;
		}

	}

	enum FailureKind {

		UNPARSEABLE,

		TRANSPORT

	}

}
