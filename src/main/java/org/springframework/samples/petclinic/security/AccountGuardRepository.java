package org.springframework.samples.petclinic.security;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface AccountGuardRepository extends JpaRepository<AccountGuard, Integer> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT g FROM AccountGuard g WHERE g.id = :id")
	Optional<AccountGuard> findByIdForUpdate(Integer id);

}
