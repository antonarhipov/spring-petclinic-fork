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

package org.springframework.samples.petclinic.vet;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VetDateExceptionRepository extends JpaRepository<VetDateException, Integer> {

	@Transactional(readOnly = true)
	List<VetDateException> findByVetId(Integer vetId);

	@Transactional(readOnly = true)
	@Query("SELECT e FROM VetDateException e WHERE e.vet.id = :vetId AND e.startDate <= :endDate AND e.endDate >= :startDate")
	List<VetDateException> findByVetIdAndDateRange(@Param("vetId") Integer vetId,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

	@Transactional(readOnly = true)
	@Query("SELECT e FROM VetDateException e WHERE e.startDate <= :endDate AND e.endDate >= :startDate")
	List<VetDateException> findByDateRange(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	@Transactional
	void deleteByVetId(Integer vetId);

}
