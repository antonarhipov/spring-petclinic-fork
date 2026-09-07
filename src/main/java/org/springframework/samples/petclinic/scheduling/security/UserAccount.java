package org.springframework.samples.petclinic.scheduling.security;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.owner.Owner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

	@Column(name = "username", nullable = false, unique = true, length = 80)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 16)
	private UserRole role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", unique = true)
	private Owner owner;

	protected UserAccount() {
	}

	public String getUsername() {
		return this.username;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public UserRole getRole() {
		return this.role;
	}

	public Owner getOwner() {
		return this.owner;
	}

}
