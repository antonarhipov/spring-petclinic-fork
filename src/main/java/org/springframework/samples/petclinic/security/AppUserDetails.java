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

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppUserDetails implements UserDetails {

	private final Integer id;

	private final String username;

	private final String passwordHash;

	private final UserRole role;

	private final boolean enabled;

	private volatile boolean mustChangePassword;

	private final Integer ownerId;

	public AppUserDetails(AppUser appUser) {
		this.id = appUser.getId();
		this.username = appUser.getUsername();
		this.passwordHash = appUser.getPasswordHash();
		this.role = appUser.getRole();
		this.enabled = appUser.isEnabled();
		this.mustChangePassword = appUser.isMustChangePassword();
		this.ownerId = appUser.getOwner() != null ? appUser.getOwner().getId() : null;
	}

	public AppUserDetails(Integer id, String username, String passwordHash, UserRole role, boolean enabled,
			boolean mustChangePassword, Integer ownerId) {
		this.id = id;
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.enabled = enabled;
		this.mustChangePassword = mustChangePassword;
		this.ownerId = ownerId;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
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
		return this.enabled;
	}

	public Integer getId() {
		return this.id;
	}

	public UserRole getRole() {
		return this.role;
	}

	public boolean isMustChangePassword() {
		return this.mustChangePassword;
	}

	public void setMustChangePassword(boolean mustChangePassword) {
		this.mustChangePassword = mustChangePassword;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

}
