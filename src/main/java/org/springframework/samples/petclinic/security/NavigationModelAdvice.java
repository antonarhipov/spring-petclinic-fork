/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package org.springframework.samples.petclinic.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NavigationModelAdvice {

	@ModelAttribute("staffUser")
	public boolean staffUser(Authentication authentication) {
		return authentication != null && authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.anyMatch("ROLE_STAFF"::equals);
	}

}
