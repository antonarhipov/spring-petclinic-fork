package org.springframework.samples.petclinic.availability;

import java.time.DayOfWeek;
import java.time.LocalTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.shared.persistence.SchedulingEntity;

@Entity
@Table(name = "recurring_shifts")
public class RecurringShift extends SchedulingEntity {

	@Column(name = "vet_id", nullable = false)
	private Integer vetId;

	@Enumerated(EnumType.STRING)
	@Column(name = "weekday", nullable = false, length = 16)
	private DayOfWeek weekday;

	@Column(name = "local_start", nullable = false)
	private LocalTime localStart;

	@Column(name = "local_end", nullable = false)
	private LocalTime localEnd;

	public RecurringShift() {
	}

	public RecurringShift(Integer vetId, DayOfWeek weekday, LocalTime localStart, LocalTime localEnd) {
		this.vetId = vetId;
		this.weekday = weekday;
		this.localStart = localStart;
		this.localEnd = localEnd;
	}

	public Integer getVetId() {
		return this.vetId;
	}

	public void setVetId(Integer vetId) {
		this.vetId = vetId;
	}

	public DayOfWeek getWeekday() {
		return this.weekday;
	}

	public void setWeekday(DayOfWeek weekday) {
		this.weekday = weekday;
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
