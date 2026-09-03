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

package org.springframework.samples.petclinic.scheduling.request;

import java.time.ZonedDateTime;

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
 * Audit event log for request transitions and actions (RULE-7, RULE-43, AC-121).
 */
@Entity
@Table(name = "scheduling_request_event")
public class SchedulingRequestEvent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id", nullable = false)
	private SchedulingRequest request;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_state", length = 50)
	private RequestState fromState;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_state", nullable = false, length = 50)
	private RequestState toState;

	@Column(name = "actor", nullable = false, length = 100)
	private String actor;

	@Column(name = "action", nullable = false, length = 100)
	private String action;

	@Column(name = "reason", length = 255)
	private String reason;

	@Column(name = "payload")
	private String payload;

	@Column(name = "timestamp", nullable = false)
	private ZonedDateTime timestamp;

	public SchedulingRequest getRequest() {
		return this.request;
	}

	public void setRequest(SchedulingRequest request) {
		this.request = request;
	}

	public RequestState getFromState() {
		return this.fromState;
	}

	public void setFromState(RequestState fromState) {
		this.fromState = fromState;
	}

	public RequestState getToState() {
		return this.toState;
	}

	public void setToState(RequestState toState) {
		this.toState = toState;
	}

	public String getActor() {
		return this.actor;
	}

	public void setActor(String actor) {
		this.actor = actor;
	}

	public String getAction() {
		return this.action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getReason() {
		return this.reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getPayload() {
		return this.payload;
	}

	public void setPayload(String payload) {
		this.payload = payload;
	}

	public ZonedDateTime getTimestamp() {
		return this.timestamp;
	}

	public void setTimestamp(ZonedDateTime timestamp) {
		this.timestamp = timestamp;
	}

}
