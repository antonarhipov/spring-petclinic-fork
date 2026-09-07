package org.springframework.samples.petclinic.scheduling.request;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.samples.petclinic.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "interpretation_failures")
public class InterpretationFailure extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "failure_kind", nullable = false, length = 32)
	private InterpretationFailureKind failureKind;

	@Lob
	@Column(name = "raw_output")
	private String rawOutput;

	@Column(name = "created_date", nullable = false)
	private LocalDate createdDate;

	@Column(name = "created_time", nullable = false)
	private LocalTime createdTime;

	protected InterpretationFailure() {
	}

	public InterpretationFailure(InterpretationFailureKind failureKind, String rawOutput, LocalDate createdDate,
			LocalTime createdTime) {
		this.failureKind = failureKind;
		this.rawOutput = rawOutput;
		this.createdDate = createdDate;
		this.createdTime = createdTime;
	}

	void attachTo(SchedulingRequest request) {
		this.request = request;
	}

	public InterpretationFailureKind getFailureKind() {
		return this.failureKind;
	}

	public String getRawOutput() {
		return this.rawOutput;
	}

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public LocalDate getCreatedDate() {
		return this.createdDate;
	}

	public LocalTime getCreatedTime() {
		return this.createdTime;
	}

}
