package org.springframework.samples.petclinic.scheduling.config;

import java.time.LocalDate;

import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_exceptions")
public class VetException {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "exception_date", nullable = false)
	private LocalDate exceptionDate;

	public VetException() {
	}

	public VetException(Vet vet, LocalDate exceptionDate) {
		this.vet = vet;
		this.exceptionDate = exceptionDate;
	}

	public Integer getId() {
		return this.id;
	}

	public Vet getVet() {
		return this.vet;
	}

	public LocalDate getExceptionDate() {
		return this.exceptionDate;
	}

}
