package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "vet_leaves")
public class VetLeave extends BaseEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column
	private String reason;

	@Version
	private long version;

	protected VetLeave() {
	}

	public VetLeave(Integer vetId, LocalDate startDate, LocalDate endDate, String reason) {
		this.vetId = vetId;
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
	}

	public boolean applies(LocalDate date) {
		return !date.isBefore(this.startDate) && !date.isAfter(this.endDate);
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public String getReason() {
		return this.reason;
	}

}
