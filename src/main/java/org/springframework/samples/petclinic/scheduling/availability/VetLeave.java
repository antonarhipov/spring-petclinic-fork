package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_leave")
public class VetLeave {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "veterinarian_id", nullable = false)
	private Integer veterinarianId;

	@Column(name = "start_local_date", nullable = false)
	private LocalDate startLocalDate;

	@Column(name = "end_local_date", nullable = false)
	private LocalDate endLocalDate;

	public Long getId() {
		return this.id;
	}

	public Integer getVeterinarianId() {
		return this.veterinarianId;
	}

	public LocalDate getStartLocalDate() {
		return this.startLocalDate;
	}

	public LocalDate getEndLocalDate() {
		return this.endLocalDate;
	}

	public void setVeterinarianId(Integer veterinarianId) {
		this.veterinarianId = veterinarianId;
	}

	public void setStartLocalDate(LocalDate startLocalDate) {
		this.startLocalDate = startLocalDate;
	}

	public void setEndLocalDate(LocalDate endLocalDate) {
		this.endLocalDate = endLocalDate;
	}

}
