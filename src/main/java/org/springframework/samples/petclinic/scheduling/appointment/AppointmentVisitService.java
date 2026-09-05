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

package org.springframework.samples.petclinic.scheduling.appointment;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistence boundary for appointment-linked visits. */
@Service
@Transactional
public class AppointmentVisitService {

	private final JdbcTemplate jdbcTemplate;

	public AppointmentVisitService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void createLinkedVisit(Appointment appointment) {
		String description = appointment.getRequest() != null ? appointment.getRequest().getReasonText()
				: appointment.getReason();
		if (description == null || description.isBlank()) {
			description = "Completed appointment";
		}
		this.jdbcTemplate.update(
				"INSERT INTO visits (pet_id, visit_date, description, appointment_id) VALUES (?, ?, ?, ?)",
				appointment.getPet().getId(), appointment.getStartTime().toLocalDate(), description,
				appointment.getId());
	}

	@Transactional(readOnly = true)
	public VisitDetails requireVisit(Integer visitId) {
		return this.jdbcTemplate.queryForObject(
				"SELECT id, pet_id, visit_date, description, appointment_id FROM visits WHERE id = ?",
				(rs, rowNum) -> new VisitDetails(rs.getInt("id"), rs.getInt("pet_id"),
						rs.getDate("visit_date").toLocalDate(), rs.getString("description"),
						rs.getObject("appointment_id", Integer.class)),
				visitId);
	}

	public void updateDescription(Integer visitId, String description) {
		int updated = this.jdbcTemplate.update("UPDATE visits SET description = ? WHERE id = ?", description, visitId);
		if (updated != 1) {
			throw new IllegalArgumentException("Visit not found: " + visitId);
		}
	}

	public record VisitDetails(Integer id, Integer petId, LocalDate date, String description, Integer appointmentId) {
	}

}
