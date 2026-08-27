package org.springframework.samples.petclinic.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {

	Optional<Account> findByUsernameIgnoreCase(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE LOWER(a.username) = LOWER(:username)")
	Optional<Account> findByUsernameIgnoreCaseForUpdate(String username);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdForUpdate(Integer id);

	Optional<Account> findByOwnerId(Integer ownerId);

	boolean existsByUsernameIgnoreCase(String username);

	List<Account> findByRoleAndActiveTrue(AccountRole role);

	long countByRoleAndActiveTrue(AccountRole role);

	List<Account> findAllByOrderByUsernameAsc();

	@Query("SELECT a.id FROM Account a WHERE a.lockedUntil IS NOT NULL AND a.lockedUntil <= :cutoff ORDER BY a.lockedUntil ASC")
	List<Integer> findExpiredLockedAccountIds(@Param("cutoff") Instant cutoff, Pageable pageable);

}
