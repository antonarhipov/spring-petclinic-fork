package org.springframework.samples.petclinic.availability;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.shared.persistence.SchedulingEntity;

@Entity
@Table(name = "availability_exception_days")
public class AvailabilityExceptionDay extends SchedulingEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Column(name = "local_date", nullable = false)
	private LocalDate localDate;

	@OneToMany(mappedBy = "exceptionDay", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<AvailabilityExceptionInterval> intervals = new ArrayList<>();

	public AvailabilityExceptionDay() {
	}

	public AvailabilityExceptionDay(Integer vetId, LocalDate localDate) {
		this.vetId = vetId;
		this.localDate = localDate;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public LocalDate getLocalDate() {
		return this.localDate;
	}

	public void setLocalDate(LocalDate localDate) {
		this.localDate = localDate;
	}

	public List<AvailabilityExceptionInterval> getIntervals() {
		return this.intervals;
	}

	public void setIntervals(List<AvailabilityExceptionInterval> intervals) {
		this.intervals = intervals;
	}

	public void addInterval(AvailabilityExceptionInterval interval) {
		this.intervals.add(interval);
		interval.setExceptionDay(this);
	}

}
