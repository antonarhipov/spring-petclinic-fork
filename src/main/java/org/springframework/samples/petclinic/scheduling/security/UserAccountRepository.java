package org.springframework.samples.petclinic.scheduling.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface UserAccountRepository extends Repository<UserAccount, Integer> {

	@EntityGraph(attributePaths = "owner")
	Optional<UserAccount> findByUsername(String username);

}
