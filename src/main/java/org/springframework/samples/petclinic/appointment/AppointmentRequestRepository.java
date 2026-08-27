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
package org.springframework.samples.petclinic.appointment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/**
 * Repository for {@link AppointmentRequest} domain objects.
 */
public interface AppointmentRequestRepository extends JpaRepository<AppointmentRequest, Integer> {

	@Query("SELECT r FROM AppointmentRequest r JOIN FETCH r.pet WHERE r.owner.id = :ownerId ORDER BY r.id DESC")
	List<AppointmentRequest> findByOwnerId(@Param("ownerId") Integer ownerId);

	@Query("SELECT r FROM AppointmentRequest r WHERE r.pet.id = :petId ORDER BY r.id DESC")
	List<AppointmentRequest> findByPetId(@Param("petId") Integer petId);

	@Query("SELECT r FROM AppointmentRequest r JOIN FETCH r.owner JOIN FETCH r.pet WHERE r.status = :status ORDER BY r.id ASC")
	List<AppointmentRequest> findByStatus(@Param("status") AppointmentRequestStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT r FROM AppointmentRequest r WHERE r.id = :id")
	java.util.Optional<AppointmentRequest> findByIdForUpdate(@Param("id") Integer id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE AppointmentRequest r SET r.activeHoldId = null WHERE r.activeHoldId IN :holdIds")
	int clearActiveHoldIds(@Param("holdIds") List<Integer> holdIds);

}
