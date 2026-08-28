package org.springframework.samples.petclinic.security;

import java.time.Instant;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String username;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountRole role;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id")
	private Owner owner;

	@Column(name = "must_change_password", nullable = false)
	private boolean mustChangePassword;

	@Column(name = "temporary_password_expires_at")
	private Instant temporaryPasswordExpiresAt;

	@Column(name = "failed_sign_in_count", nullable = false)
	private int failedSignInCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Version
	private long version;

	protected Account() {
	}

	public Account(String username, String passwordHash, AccountRole role, Owner owner) {
		this.username = username.strip().toLowerCase();
		this.passwordHash = passwordHash;
		this.role = role;
		this.owner = owner;
	}

	public String getUsername() {
		return this.username;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public AccountRole getRole() {
		return this.role;
	}

	public Owner getOwner() {
		return this.owner;
	}

	public boolean isMustChangePassword() {
		return this.mustChangePassword;
	}

	public boolean isTemporaryPasswordExpired(Instant now) {
		return this.mustChangePassword && this.temporaryPasswordExpiresAt != null
				&& !this.temporaryPasswordExpiresAt.isAfter(now);
	}

	public boolean isLocked(Instant now) {
		return this.lockedUntil != null && this.lockedUntil.isAfter(now);
	}

	public void requirePasswordChangeUntil(Instant expiresAt) {
		this.mustChangePassword = true;
		this.temporaryPasswordExpiresAt = expiresAt;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.mustChangePassword = false;
		this.temporaryPasswordExpiresAt = null;
		this.failedSignInCount = 0;
		this.lockedUntil = null;
	}

	public void signInSucceeded() {
		this.failedSignInCount = 0;
		this.lockedUntil = null;
	}

	public void signInFailed(Instant now, int maximumAttempts, int lockMinutes) {
		this.failedSignInCount++;
		if (this.failedSignInCount >= maximumAttempts) {
			this.lockedUntil = now.plusSeconds(lockMinutes * 60L);
			this.failedSignInCount = 0;
		}
	}

}
