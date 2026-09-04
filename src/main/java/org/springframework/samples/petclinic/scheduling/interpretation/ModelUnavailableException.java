/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

/** Signals that the configured interpretation model could not produce a result. */
public class ModelUnavailableException extends RuntimeException {

	public ModelUnavailableException(String reason, Throwable cause) {
		super(reason, cause);
	}

}
