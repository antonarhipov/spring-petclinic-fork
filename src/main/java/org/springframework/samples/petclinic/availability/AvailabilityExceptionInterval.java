package org.springframework.samples.petclinic.availability;

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
@Table(name = "availability_exception_intervals")
public class AvailabilityExceptionInterval {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "exception_day_id", nullable = false)
	private AvailabilityExceptionDay exceptionDay;

	@Column(name = "local_start", nullable = false)
	private LocalTime localStart;

	@Column(name = "local_end", nullable = false)
	private LocalTime localEnd;

	public AvailabilityExceptionInterval() {
	}

	public AvailabilityExceptionInterval(LocalTime localStart, LocalTime localEnd) {
		this.localStart = localStart;
		this.localEnd = localEnd;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public AvailabilityExceptionDay getExceptionDay() {
		return this.exceptionDay;
	}

	public void setExceptionDay(AvailabilityExceptionDay exceptionDay) {
		this.exceptionDay = exceptionDay;
	}

	public LocalTime getLocalStart() {
		return this.localStart;
	}

	public void setLocalStart(LocalTime localStart) {
		this.localStart = localStart;
	}

	public LocalTime getLocalEnd() {
		return this.localEnd;
	}

	public void setLocalEnd(LocalTime localEnd) {
		this.localEnd = localEnd;
	}

}
