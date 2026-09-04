/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.request;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link SchedulingRequest}.
 */
public interface SchedulingRequestRepository extends JpaRepository<SchedulingRequest, Integer> {

	@Transactional(readOnly = true)
	List<SchedulingRequest> findByOwnerId(Integer ownerId);

	/**
	 * Owner-scoped read model: loads every association the owner pages render so the
	 * detached entity can be rendered with {@code spring.jpa.open-in-view=false}.
	 */
	@Transactional(readOnly = true)
	@EntityGraph(attributePaths = { "pet", "owner", "heldVet" }, type = EntityGraph.EntityGraphType.LOAD)
	Optional<SchedulingRequest> findByIdAndOwnerId(Integer id, Integer ownerId);

	@Transactional(readOnly = true)
	Optional<SchedulingRequest> findByActivePetId(Integer petId);

	@Transactional(readOnly = true)
	List<SchedulingRequest> findByState(RequestState state);

	@Transactional(readOnly = true)
	@Query("SELECT r FROM SchedulingRequest r WHERE r.state NOT IN ('ACCEPTED', 'ABANDONED') ORDER BY r.createdAt ASC")
	List<SchedulingRequest> findAllOpen();

	@Transactional(readOnly = true)
	@Query("SELECT r FROM SchedulingRequest r WHERE r.heldVet.id = :vetId AND r.state = 'SUGGESTION_OFFERED'")
	List<SchedulingRequest> findActiveHoldsByVetId(@Param("vetId") Integer vetId);

	@Transactional(readOnly = true)
	@Query("SELECT r FROM SchedulingRequest r WHERE r.state = 'SUGGESTION_OFFERED' AND r.heldVet IS NOT NULL")
	List<SchedulingRequest> findAllActiveHolds();

}
