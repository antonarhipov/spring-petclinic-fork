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

package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data JPA repository for {@link Appointment}.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Owner o JOIN o.pets p, Appointment a WHERE o.id = :ownerId AND p = a.pet AND a.id = :appointmentId")
	Optional<Appointment> findOwnedById(@Param("ownerId") Integer ownerId,
			@Param("appointmentId") Integer appointmentId);

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Owner o JOIN o.pets p, Appointment a WHERE o.id = :ownerId AND p = a.pet ORDER BY a.startTime")
	List<Appointment> findAllOwnedBy(@Param("ownerId") Integer ownerId);

	@Transactional(readOnly = true)
	List<Appointment> findByPetId(Integer petId);

	@Transactional(readOnly = true)
	List<Appointment> findByVetId(Integer vetId);

	@Transactional(readOnly = true)
	List<Appointment> findByStatus(AppointmentStatus status);

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Appointment a WHERE a.vet.id = :vetId AND a.status = 'CONFIRMED' AND a.startTime >= :start AND a.startTime < :end")
	List<Appointment> findConfirmedByVetIdAndDateRange(@Param("vetId") Integer vetId,
			@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Appointment a WHERE a.status = 'CONFIRMED' AND a.startTime >= :start AND a.startTime < :end")
	List<Appointment> findConfirmedInDateRange(@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Appointment a WHERE a.pet.id = :petId AND a.status = 'CONFIRMED' AND a.startTime >= :start AND a.startTime < :end")
	List<Appointment> findConfirmedByPetIdAndDateRange(@Param("petId") Integer petId,
			@Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);

	@Transactional(readOnly = true)
	@Query("SELECT a FROM Appointment a WHERE a.pet.id = :petId AND a.status = 'CONFIRMED' AND a.startTime >= :now")
	List<Appointment> findUpcomingByPetId(@Param("petId") Integer petId, @Param("now") ZonedDateTime now);

}
