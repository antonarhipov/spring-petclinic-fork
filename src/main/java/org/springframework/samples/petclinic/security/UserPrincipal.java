package org.springframework.samples.petclinic.security;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails, Serializable {

	private static final long serialVersionUID = 1L;

	private final Integer id;

	private final String username;

	private String password;

	private final AccountRole role;

	private final Integer ownerId;

	private final boolean passwordChangeRequired;

	private final boolean active;

	private final Instant lockedUntil;

	public UserPrincipal(Integer id, String username, String password, AccountRole role, Integer ownerId,
			boolean passwordChangeRequired, boolean active, Instant lockedUntil) {
		this.id = id;
		this.username = username;
		this.password = password;
		this.role = role;
		this.ownerId = ownerId;
		this.passwordChangeRequired = passwordChangeRequired;
		this.active = active;
		this.lockedUntil = lockedUntil;
	}

	public static UserPrincipal fromAccount(Account account) {
		return new UserPrincipal(account.getId(), account.getUsername(), account.getPassword(), account.getRole(),
				account.getOwner() != null ? account.getOwner().getId() : null, account.isPasswordChangeRequired(),
				account.isActive(), account.getLockedUntil());
	}

	public Integer getId() {
		return id;
	}

	public AccountRole getRole() {
		return role;
	}

	public Integer getOwnerId() {
		return ownerId;
	}

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return lockedUntil == null || Instant.now().isAfter(lockedUntil);
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return active;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		UserPrincipal that = (UserPrincipal) o;
		return Objects.equals(username, that.username);
	}

	@Override
	public int hashCode() {
		return Objects.hash(username);
	}

	@Override
	public String toString() {
		return "UserPrincipal{" + "id=" + id + ", username='" + username + '\'' + ", role=" + role + ", ownerId="
				+ ownerId + ", passwordChangeRequired=" + passwordChangeRequired + ", active=" + active + '}';
	}

}
