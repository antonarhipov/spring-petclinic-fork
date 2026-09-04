/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.clinic;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VetWeeklyBlockRepository extends JpaRepository<VetWeeklyBlock, Integer> {

	List<VetWeeklyBlock> findByVetId(Integer vetId);

}
