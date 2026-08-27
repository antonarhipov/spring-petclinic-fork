package org.springframework.samples.petclinic.security;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_guard")
public class AccountGuard {

	@Id
	private Integer id;

	public AccountGuard() {
	}

	public AccountGuard(Integer id) {
		this.id = id;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

}
