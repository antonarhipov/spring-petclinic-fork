package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	Optional<Account> findByUsername(String username);

}
