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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

	@Override
	public String convertToDatabaseColumn(UserRole role) {
		return role == null ? null : role.databaseValue();
	}

	@Override
	public UserRole convertToEntityAttribute(String value) {
		if (value == null) {
			return null;
		}
		for (UserRole role : UserRole.values()) {
			if (role.databaseValue().equals(value)) {
				return role;
			}
		}
		throw new IllegalArgumentException("Unsupported account role");
	}

}
