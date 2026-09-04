/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.clinic;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicConfigService {

	private final ClinicConfigRepository repository;

	public ClinicConfigService(ClinicConfigRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public ClinicConfig current() {
		return this.repository.findById(1).orElseThrow();
	}

}
