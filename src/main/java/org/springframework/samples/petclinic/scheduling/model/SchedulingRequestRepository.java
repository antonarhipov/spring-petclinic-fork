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

package org.springframework.samples.petclinic.scheduling.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@Transactional(readOnly = true)
	Optional<SchedulingRequest> findByActivePetKey(Integer activePetKey);

	/**
	 * Loads a request together with its {@link SchedulingRequest#getOwner() owner} and
	 * {@link SchedulingRequest#getPet() pet}. Both associations are
	 * {@code @ManyToOne(fetch = LAZY)}, so with {@code spring.jpa.open-in-view=false}
	 * they must be fetched eagerly here; otherwise the status view fails with a
	 * {@code LazyInitializationException} once the session is closed.
	 */
	@Transactional(readOnly = true)
	@Query("SELECT sr FROM SchedulingRequest sr LEFT JOIN FETCH sr.owner LEFT JOIN FETCH sr.pet WHERE sr.id = :id")
	Optional<SchedulingRequest> findByIdWithOwnerAndPet(@Param("id") Integer id);

	@Transactional(readOnly = true)
	List<SchedulingRequest> findByOwnerId(Integer ownerId);

	/**
	 * Loads all requests owned by the given owner together with their
	 * {@link SchedulingRequest#getOwner() owner} and {@link SchedulingRequest#getPet()
	 * pet}. Both associations are {@code @ManyToOne(fetch = LAZY)}, so with
	 * {@code spring.jpa.open-in-view=false} they must be fetched eagerly here; otherwise
	 * the "My Appointments" view fails with a {@code LazyInitializationException} once
	 * the session is closed.
	 */
	@Transactional(readOnly = true)
	@Query("SELECT sr FROM SchedulingRequest sr LEFT JOIN FETCH sr.owner LEFT JOIN FETCH sr.pet WHERE sr.owner.id = :ownerId")
	List<SchedulingRequest> findByOwnerIdWithPet(@Param("ownerId") Integer ownerId);

	@Transactional(readOnly = true)
	List<SchedulingRequest> findByPetId(Integer petId);

	@Transactional(readOnly = true)
	List<SchedulingRequest> findByState(RequestState state);

	/**
	 * Loads all requests in the given state together with their
	 * {@link SchedulingRequest#getOwner() owner} and {@link SchedulingRequest#getPet()
	 * pet}. Both associations are {@code @ManyToOne(fetch = LAZY)}, so with
	 * {@code spring.jpa.open-in-view=false} they must be fetched eagerly here; otherwise
	 * the staff queue view fails with a {@code LazyInitializationException} once the
	 * session is closed.
	 */
	@Transactional(readOnly = true)
	@Query("SELECT sr FROM SchedulingRequest sr LEFT JOIN FETCH sr.owner LEFT JOIN FETCH sr.pet WHERE sr.state = :state")
	List<SchedulingRequest> findByStateWithOwnerAndPet(@Param("state") RequestState state);

}
