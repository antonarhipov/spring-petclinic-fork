/* Copyright 2012-2026 the original author or authors. Licensed under the Apache License, Version 2.0. */
package org.springframework.samples.petclinic.scheduling.clinic;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

@Entity
@Table(name = "vet_exception")
public class VetException extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "exception_date", nullable = false)
	private LocalDate exceptionDate;

	@Column(name = "unavailable", nullable = false)
	private boolean unavailable;

	public LocalDate getExceptionDate() {
		return this.exceptionDate;
	}

	public boolean isUnavailable() {
		return this.unavailable;
	}

}
