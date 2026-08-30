package org.springframework.samples.petclinic.scheduling.availability;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_date_exception_intervals")
public class VetDateExceptionInterval {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "exception_id", nullable = false)
	private VetDateException exception;

	@Column(name = "start_local_time", nullable = false)
	private LocalTime startLocalTime;

	@Column(name = "end_local_time", nullable = false)
	private LocalTime endLocalTime;

	public Long getId() {
		return this.id;
	}

	public LocalTime getStartLocalTime() {
		return this.startLocalTime;
	}

	public LocalTime getEndLocalTime() {
		return this.endLocalTime;
	}

	public void setException(VetDateException exception) {
		this.exception = exception;
	}

	public void setStartLocalTime(LocalTime startLocalTime) {
		this.startLocalTime = startLocalTime;
	}

	public void setEndLocalTime(LocalTime endLocalTime) {
		this.endLocalTime = endLocalTime;
	}

}
