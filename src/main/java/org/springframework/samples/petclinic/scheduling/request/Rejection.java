package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;
import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "request_rejections")
public class Rejection extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Column(name = "appointment_date", nullable = false)
	private LocalDate appointmentDate;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	protected Rejection() {
	}

	public Rejection(Vet vet, LocalDate appointmentDate, LocalTime startTime) {
		this.vet = vet;
		this.appointmentDate = appointmentDate;
		this.startTime = startTime;
	}

	void attachTo(SchedulingRequest request) {
		this.request = request;
	}

	public Vet getVet() {
		return this.vet;
	}

	public LocalDate getAppointmentDate() {
		return this.appointmentDate;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

}
