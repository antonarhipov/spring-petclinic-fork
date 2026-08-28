package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalDate;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "clinic_closures")
public class ClinicClosure extends BaseEntity {

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	@Column
	private String reason;

	@Version
	private long version;

	protected ClinicClosure() {
	}

	public ClinicClosure(LocalDate startDate, LocalDate endDate, String reason) {
		this.startDate = startDate;
		this.endDate = endDate;
		this.reason = reason;
	}

	public boolean applies(LocalDate date) {
		return !date.isBefore(this.startDate) && !date.isAfter(this.endDate);
	}

}
