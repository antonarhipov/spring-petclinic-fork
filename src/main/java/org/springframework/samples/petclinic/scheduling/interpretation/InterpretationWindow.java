/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.samples.petclinic.model.BaseEntity;

/**
 * Persistent availability window child entity (RULE-1, RULE-8).
 */
@Entity
@Table(name = "interpretation_window")
public class InterpretationWindow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "interpretation_id", nullable = false)
	private Interpretation interpretation;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 20)
	private WindowKind kind;

	@Column(name = "date_val")
	private LocalDate dateVal;

	@Column(name = "start_date")
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "day_of_week", length = 20)
	private DayOfWeek dayOfWeek;

	@Column(name = "start_time")
	private LocalTime startTime;

	@Column(name = "end_time")
	private LocalTime endTime;

	@Column(name = "tokens")
	private String tokens;

	public Interpretation getInterpretation() {
		return this.interpretation;
	}

	public void setInterpretation(Interpretation interpretation) {
		this.interpretation = interpretation;
	}

	public WindowKind getKind() {
		return this.kind;
	}

	public void setKind(WindowKind kind) {
		this.kind = kind;
	}

	public LocalDate getDateVal() {
		return this.dateVal;
	}

	public void setDateVal(LocalDate dateVal) {
		this.dateVal = dateVal;
	}

	public LocalDate getStartDate() {
		return this.startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public LocalDate getEndDate() {
		return this.endDate;
	}

	public void setEndDate(LocalDate endDate) {
		this.endDate = endDate;
	}

	public DayOfWeek getDayOfWeek() {
		return this.dayOfWeek;
	}

	public void setDayOfWeek(DayOfWeek dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public LocalTime getStartTime() {
		return this.startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return this.endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public String getTokens() {
		return this.tokens;
	}

	public void setTokens(String tokens) {
		this.tokens = tokens;
	}

}
