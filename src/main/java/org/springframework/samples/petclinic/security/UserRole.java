/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.security;

/** The complete and closed account-role set. */
public enum UserRole {

	OWNER("owner"), STAFF("staff");

	private final String databaseValue;

	UserRole(String databaseValue) {
		this.databaseValue = databaseValue;
	}

	public String databaseValue() {
		return this.databaseValue;
	}

}
