package org.springframework.samples.petclinic.scheduling.config;

import java.time.DayOfWeek;
import java.time.LocalTime;

import org.springframework.samples.petclinic.vet.Vet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "vet_working_blocks")
public class VetWorkingBlock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@ManyToOne
	@JoinColumn(name = "vet_id", nullable = false)
	private Vet vet;

	@Enumerated(EnumType.STRING)
	@Column(name = "weekday", nullable = false, length = 16)
	private DayOfWeek weekday;

	@Column(name = "start_time", nullable = false)
	private LocalTime startTime;

	@Column(name = "end_time", nullable = false)
	private LocalTime endTime;

	public VetWorkingBlock() {
	}

	public VetWorkingBlock(Vet vet, DayOfWeek weekday, LocalTime startTime, LocalTime endTime) {
		this.vet = vet;
		this.weekday = weekday;
		this.startTime = startTime;
		this.endTime = endTime;
	}

	public Integer getId() {
		return this.id;
	}

	public Vet getVet() {
		return this.vet;
	}

	public DayOfWeek getWeekday() {
		return this.weekday;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

}
