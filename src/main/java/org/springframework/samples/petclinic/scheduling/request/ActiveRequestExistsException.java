/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.request;

public class ActiveRequestExistsException extends IllegalStateException {

	public ActiveRequestExistsException() {
		super("An active request already exists");
	}

}
