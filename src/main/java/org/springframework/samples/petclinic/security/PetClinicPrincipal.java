package org.springframework.samples.petclinic.security;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class PetClinicPrincipal implements UserDetails {

	private final Long id;

	private final String username;

	private final String password;

	private final Role role;

	private final Integer ownerId;

	private final boolean enabled;

	private final Instant temporaryPasswordExpiresAt;

	private final boolean passwordChangeRequired;

	private final long sessionVersion;

	private final Collection<? extends GrantedAuthority> authorities;

	public PetClinicPrincipal(Long id, String username, String password, Role role, Integer ownerId, boolean enabled,
			Instant temporaryPasswordExpiresAt, boolean passwordChangeRequired, long sessionVersion) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.role = role;
		this.ownerId = ownerId;
		this.enabled = enabled;
		this.temporaryPasswordExpiresAt = temporaryPasswordExpiresAt;
		this.passwordChangeRequired = passwordChangeRequired;
		this.sessionVersion = sessionVersion;
		this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	public Long getId() {
		return this.id;
	}

	public Long getAccountId() {
		return this.id;
	}

	public Role getRole() {
		return this.role;
	}

	public Integer getOwnerId() {
		return this.ownerId;
	}

	public Instant getTemporaryPasswordExpiresAt() {
		return this.temporaryPasswordExpiresAt;
	}

	public boolean isPasswordChangeRequired() {
		return this.passwordChangeRequired;
	}

	public long getSessionVersion() {
		return this.sessionVersion;
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
		if (this.temporaryPasswordExpiresAt != null) {
			return Instant.now().isBefore(this.temporaryPasswordExpiresAt);
		}
		return true;
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

}
