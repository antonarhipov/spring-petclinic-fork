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
@Table(name = "vet_leave")
public class VetLeave {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	public VetLeave() {
	}

	public VetLeave(Vet vet, LocalDate startDate, LocalDate endDate) {
		this.vet = vet;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	public Integer getId() {
		return this.id;
	}

	public Vet getVet() {
		return this.vet;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public boolean covers(LocalDate date) {
		return !date.isBefore(this.startDate) && !date.isAfter(this.endDate);
	}

}
