/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utility helpers to access the authenticated user identity and owner ID from the
 * security context.
 */
public final class SecurityUtils {

	private SecurityUtils() {
	}

	public static Optional<PetClinicUserDetails> getCurrentUserDetails() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicUserDetails userDetails) {
			return Optional.of(userDetails);
		}
		return Optional.empty();
	}

	public static Optional<String> getCurrentUsername() {
		return getCurrentUserDetails().map(PetClinicUserDetails::getUsername);
	}

	public static Optional<Integer> getCurrentOwnerId() {
		return getCurrentUserDetails().map(PetClinicUserDetails::getOwnerId);
	}

	public static boolean isOwner() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_OWNER".equals(a.getAuthority()));
	}

	public static boolean isStaff() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null) {
			return false;
		}
		return authentication.getAuthorities().stream().anyMatch(a -> "ROLE_STAFF".equals(a.getAuthority()));
	}

}
