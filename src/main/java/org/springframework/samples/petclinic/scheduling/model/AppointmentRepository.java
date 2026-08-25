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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Transactional(readOnly = true)
	Optional<Appointment> findBySchedulingRequestId(Integer requestId);

	@Transactional(readOnly = true)
	List<Appointment> findByOwnerId(Integer ownerId);

	@Transactional(readOnly = true)
	List<Appointment> findByPetId(Integer petId);

	@Transactional(readOnly = true)
	List<Appointment> findByOwnerIdAndStatusAndStartTimeAfterOrderByStartTimeAsc(Integer ownerId,
			AppointmentStatus status, LocalDateTime startTime);

	@Transactional(readOnly = true)
	List<Appointment> findByOwnerIdOrderByStartTimeDesc(Integer ownerId);

	@Transactional(readOnly = true)
	List<Appointment> findByVetIdAndStartTimeBetween(Integer vetId, LocalDateTime start, LocalDateTime end);

	@Transactional(readOnly = true)
	List<Appointment> findByStartTimeBetweenOrderByStartTimeAsc(LocalDateTime start, LocalDateTime end);

	@Transactional(readOnly = true)
	List<Appointment> findByStatusOrderByStartTimeAsc(AppointmentStatus status);

	@Transactional(readOnly = true)
	List<Appointment> findAllByOrderByStartTimeAsc();

}
