package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Integer> {

	Optional<Account> findByUsername(String username);

	@EntityGraph(attributePaths = { "owner", "owner.pets" })
	@Query("select account from Account account where account.username = :username")
	Optional<Account> findOwnerAccessByUsername(String username);

}
