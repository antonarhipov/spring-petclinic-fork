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

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * PetClinic implementation of {@link UserDetails}.
 */
public class PetClinicUserDetails implements UserDetails {

	private final Integer userId;

	private final String username;

	private final String password;

	private final String role;

	private final Integer ownerId;

	private final Collection<? extends GrantedAuthority> authorities;

	public PetClinicUserDetails(User user) {
		this.userId = user.getId();
		this.username = user.getUsername();
		this.password = user.getPassword();
		this.role = user.getRole();
		this.ownerId = (user.getOwner() != null) ? user.getOwner().getId() : null;
		this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()));
	}

	public Integer getUserId() {
		return this.userId;
	}

	public String getRole() {
		return this.role;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.authorities;
	}

	@Override
	public String getPassword() {
		return this.password;
	}

	@Override
	public String getUsername() {
		return this.username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}
