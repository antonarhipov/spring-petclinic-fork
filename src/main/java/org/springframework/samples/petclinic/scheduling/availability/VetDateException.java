package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_date_exceptions")
public class VetDateException {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "veterinarian_id", nullable = false)
	private Integer veterinarianId;

	@Column(name = "exception_date", nullable = false)
	private LocalDate exceptionDate;

	@OneToMany(mappedBy = "exception", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<VetDateExceptionInterval> intervals = new ArrayList<>();

	public Long getId() {
		return this.id;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public LocalDate getExceptionDate() {
		return this.exceptionDate;
	}

	public List<VetDateExceptionInterval> getIntervals() {
		return this.intervals;
	}

	public void setVeterinarianId(Integer veterinarianId) {
		this.veterinarianId = veterinarianId;
	}

	public void setExceptionDate(LocalDate exceptionDate) {
		this.exceptionDate = exceptionDate;
	}

}
