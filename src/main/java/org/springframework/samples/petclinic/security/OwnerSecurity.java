/*
 * Copyright 2012-2025 the original author or authors.
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

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component("ownerSecurity")
public class OwnerSecurity {

	private final AppUserRepository userRepository;

	public OwnerSecurity(AppUserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public boolean canAccessOwner(Integer ownerId, Authentication authentication) {
		if (authentication == null || !authentication.isAuthenticated() || ownerId == null) {
			return false;
		}
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if ("ROLE_STAFF".equals(authority.getAuthority())) {
				return true;
			}
		}
		Object principal = authentication.getPrincipal();
		if (principal instanceof AppUserDetails userDetails) {
			return ownerId.equals(userDetails.getOwnerId());
		}
		if (principal instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
			return this.userRepository.findByUsername(userDetails.getUsername())
				.map(user -> user.getOwner() != null && ownerId.equals(user.getOwner().getId()))
				.orElse(false);
		}
		if (principal instanceof String username) {
			return this.userRepository.findByUsername(username)
				.map(user -> user.getOwner() != null && ownerId.equals(user.getOwner().getId()))
				.orElse(false);
		}
		return false;
	}

}
