package org.springframework.samples.petclinic.security;

import java.time.Instant;
import java.util.Locale;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

	@Column(name = "username", nullable = false, unique = true, length = 100)
	private String username;

	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20, updatable = false)
	private AccountRole role;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "password_change_required", nullable = false)
	private boolean passwordChangeRequired = false;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", unique = true, updatable = false)
	private Owner owner;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount = 0;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Account() {
	}

	@PrePersist
	public void onPrePersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) {
			this.createdAt = now;
		}
		if (this.updatedAt == null) {
			this.updatedAt = now;
		}
		if (this.username != null) {
			this.username = this.username.toLowerCase(Locale.ROOT).trim();
		}
	}

	@PreUpdate
	public void onPreUpdate() {
		this.updatedAt = Instant.now();
		if (this.username != null) {
			this.username = this.username.toLowerCase(Locale.ROOT).trim();
		}
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = (username != null) ? username.toLowerCase(Locale.ROOT).trim() : null;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public AccountRole getRole() {
		return role;
	}

	public void setRole(AccountRole role) {
		this.role = role;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public boolean isPasswordChangeRequired() {
		return passwordChangeRequired;
	}

	public void setPasswordChangeRequired(boolean passwordChangeRequired) {
		this.passwordChangeRequired = passwordChangeRequired;
	}

	public Owner getOwner() {
		return owner;
	}

	public void setOwner(Owner owner) {
		this.owner = owner;
	}

	public int getFailedLoginCount() {
		return failedLoginCount;
	}

	public void setFailedLoginCount(int failedLoginCount) {
		this.failedLoginCount = failedLoginCount;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public void setLockedUntil(Instant lockedUntil) {
		this.lockedUntil = lockedUntil;
	}

	public Long getVersion() {
		return version;
	}

	public void setVersion(Long version) {
		this.version = version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	@Override
	public String toString() {
		return "Account{" + "id=" + getId() + ", username='" + username + '\'' + ", role=" + role + ", active=" + active
				+ ", passwordChangeRequired=" + passwordChangeRequired + ", failedLoginCount=" + failedLoginCount
				+ ", lockedUntil=" + lockedUntil + ", version=" + version + '}';
	}

}
