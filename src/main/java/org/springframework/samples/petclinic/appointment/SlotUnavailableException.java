/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.appointment;

public class SlotUnavailableException extends RuntimeException {

	public SlotUnavailableException() {
		super("The selected slot is no longer available");
	}

	public SlotUnavailableException(Throwable cause) {
		super("The selected slot is no longer available", cause);
	}

}
