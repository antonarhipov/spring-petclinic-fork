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

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for {@link Appointment} domain objects.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Integer> {

	@Query("SELECT a FROM Appointment a WHERE a.pet.id = :petId ORDER BY a.startInstant ASC")
	List<Appointment> findByPetId(@Param("petId") Integer petId);

	@Query("SELECT a FROM Appointment a WHERE a.vet.id = :vetId ORDER BY a.startInstant ASC")
	List<Appointment> findByVetId(@Param("vetId") Integer vetId);

	@Query("SELECT a FROM Appointment a WHERE a.vet.id = :vetId AND a.status <> :excludedStatus ORDER BY a.startInstant ASC")
	List<Appointment> findByVetIdAndStatusNot(@Param("vetId") Integer vetId,
			@Param("excludedStatus") AppointmentStatus excludedStatus);

	@Query("SELECT a FROM Appointment a WHERE a.vet.id = :vetId AND a.status <> :excludedStatus AND a.startInstant >= :fromInstant AND a.startInstant < :toInstant ORDER BY a.startInstant ASC")
	List<Appointment> findByVetIdAndStatusNotAndStartInstantBetween(@Param("vetId") Integer vetId,
			@Param("excludedStatus") AppointmentStatus excludedStatus, @Param("fromInstant") Instant fromInstant,
			@Param("toInstant") Instant toInstant);

	@Query("SELECT a FROM Appointment a WHERE a.pet.id IN :petIds ORDER BY a.startInstant ASC")
	List<Appointment> findByPetIds(@Param("petIds") List<Integer> petIds);

}
