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

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.samples.petclinic.owner.Owner;
import org.springframework.samples.petclinic.owner.OwnerRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class UserAccountRepositoryTests {

	@Autowired
	private UserAccountRepository userAccounts;

	@Autowired
	private OwnerRepository owners;

	@Autowired
	private EntityManager entityManager;

	/**
	 * Reproduces the {@code LazyInitializationException} that occurred when rendering
	 * {@code owners/ownerDetails} for the authenticated owner. With
	 * {@code spring.jpa.open-in-view=false} the Hibernate session is closed once the
	 * repository call returns, so {@code UserAccount.owner} must be fetched eagerly by
	 * {@link UserAccountRepository#findByUsername(String)}; otherwise the returned owner
	 * is an uninitialized proxy that fails during view rendering.
	 */
	@Test
	void findByUsernameEagerlyInitializesLinkedOwner() {
		Owner owner = this.owners.findById(1).orElseThrow();
		UserAccount account = new UserAccount("owner1_user", "secret", UserRole.OWNER, false, owner);
		this.userAccounts.saveAndFlush(account);

		// Detach everything so findByUsername performs a fresh load, mirroring a new
		// request without an open session.
		this.entityManager.clear();

		UserAccount loaded = this.userAccounts.findByUsername("owner1_user").orElseThrow();

		assertThat(loaded.getOwner()).isNotNull();
		assertThat(Hibernate.isInitialized(loaded.getOwner())).isTrue();
		assertThat(loaded.getOwner().getFirstName()).isNotBlank();
	}

}
