/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Identical response for a missing resource and a resource owned by someone else. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OwnerResourceNotFoundException extends RuntimeException {

	public OwnerResourceNotFoundException() {
		super("Resource not found");
	}

}
