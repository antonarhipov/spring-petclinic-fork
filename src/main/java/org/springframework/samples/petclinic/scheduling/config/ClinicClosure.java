package org.springframework.samples.petclinic.scheduling.config;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinic_closures")
public class ClinicClosure {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "closure_date", nullable = false, unique = true)
	private LocalDate closureDate;

	public ClinicClosure() {
	}

	public ClinicClosure(LocalDate closureDate) {
		this.closureDate = closureDate;
	}

	public Integer getId() {
		return this.id;
	}

	public LocalDate getClosureDate() {
		return this.closureDate;
	}

}
