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

package org.springframework.samples.petclinic.schema;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates Flyway schema migrations, seeded normative data, constraints, and index
 * behavior.
 */
@SpringBootTest
class SchemaValidationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Test
	void flywayMigrationsAppliedCleanly() {
		List<Map<String, Object>> migrations = jdbcTemplate.queryForList(
				"SELECT \"version\", \"description\", \"type\", \"success\" FROM \"flyway_schema_history\" WHERE \"version\" IS NOT NULL ORDER BY \"installed_rank\"");

		assertThat(migrations).hasSize(4);
		assertThat(migrations.get(0).get("version")).isEqualTo("1");
		assertThat(migrations.get(0).get("description")).isEqualTo("stock schema");
		assertThat(migrations.get(0).get("success")).isEqualTo(true);

		assertThat(migrations.get(1).get("version")).isEqualTo("2");
		assertThat(migrations.get(1).get("description")).isEqualTo("stock data");
		assertThat(migrations.get(1).get("success")).isEqualTo(true);

		assertThat(migrations.get(2).get("version")).isEqualTo("3");
		assertThat(migrations.get(2).get("description")).isEqualTo("scheduling schema");
		assertThat(migrations.get(2).get("success")).isEqualTo(true);

		assertThat(migrations.get(3).get("version")).isEqualTo("4");
		assertThat(migrations.get(3).get("description")).isEqualTo("scheduling seed");
		assertThat(migrations.get(3).get("success")).isEqualTo(true);
	}

	@Test
	void seededAccountsMatchExactValuesAndVerifyPasswords() {
		List<Map<String, Object>> users = jdbcTemplate
			.queryForList("SELECT username, password, role, owner_id FROM users ORDER BY id");

		assertThat(users).hasSize(12);

		Map<String, String> expectedOwnerAccounts = Map.of("george", "george123", "betty", "betty123", "eduardo",
				"eduardo123", "harold", "harold123", "peter", "peter123", "jean", "jean123", "jeff", "jeff123", "maria",
				"maria123", "david", "david123", "carlos", "carlos123");

		int ownerIndex = 1;
		for (Map<String, Object> row : users) {
			String username = (String) row.get("USERNAME");
			String passwordHash = (String) row.get("PASSWORD");
			String role = (String) row.get("ROLE");
			Integer ownerId = (Integer) row.get("OWNER_ID");

			if ("owner".equals(role)) {
				assertThat(expectedOwnerAccounts).containsKey(username);
				String rawPassword = expectedOwnerAccounts.get(username);
				assertThat(passwordEncoder.matches(rawPassword, passwordHash)).isTrue();
				assertThat(ownerId).isEqualTo(ownerIndex);
				ownerIndex++;
			}
			else if ("staff".equals(role)) {
				assertThat(username).isIn("admin", "staff");
				String rawPassword = username + "123";
				assertThat(passwordEncoder.matches(rawPassword, passwordHash)).isTrue();
				assertThat(ownerId).isNull();
			}
			else {
				org.junit.jupiter.api.Assertions.fail("Unexpected role: " + role);
			}
		}
	}

	@Test
	void seededClinicOpeningHoursMatchExactValues() {
		List<Map<String, Object>> hours = jdbcTemplate.queryForList(
				"SELECT clinic_name, day_of_week, open_time, close_time, closed FROM clinic_opening_hour ORDER BY id");

		assertThat(hours).hasSize(7);
		assertThat(hours.get(0).get("DAY_OF_WEEK")).isEqualTo("MONDAY");
		assertThat(hours.get(0).get("OPEN_TIME")).isEqualTo(Time.valueOf("09:00:00"));
		assertThat(hours.get(0).get("CLOSE_TIME")).isEqualTo(Time.valueOf("17:00:00"));
		assertThat(hours.get(0).get("CLOSED")).isEqualTo(false);

		assertThat(hours.get(1).get("DAY_OF_WEEK")).isEqualTo("TUESDAY");
		assertThat(hours.get(1).get("OPEN_TIME")).isEqualTo(Time.valueOf("09:00:00"));
		assertThat(hours.get(1).get("CLOSE_TIME")).isEqualTo(Time.valueOf("17:00:00"));
		assertThat(hours.get(1).get("CLOSED")).isEqualTo(false);

		assertThat(hours.get(2).get("DAY_OF_WEEK")).isEqualTo("WEDNESDAY");
		assertThat(hours.get(2).get("OPEN_TIME")).isEqualTo(Time.valueOf("09:00:00"));
		assertThat(hours.get(2).get("CLOSE_TIME")).isEqualTo(Time.valueOf("18:00:00"));
		assertThat(hours.get(2).get("CLOSED")).isEqualTo(false);

		assertThat(hours.get(3).get("DAY_OF_WEEK")).isEqualTo("THURSDAY");
		assertThat(hours.get(3).get("OPEN_TIME")).isEqualTo(Time.valueOf("09:00:00"));
		assertThat(hours.get(3).get("CLOSE_TIME")).isEqualTo(Time.valueOf("17:00:00"));
		assertThat(hours.get(3).get("CLOSED")).isEqualTo(false);

		assertThat(hours.get(4).get("DAY_OF_WEEK")).isEqualTo("FRIDAY");
		assertThat(hours.get(4).get("OPEN_TIME")).isEqualTo(Time.valueOf("10:00:00"));
		assertThat(hours.get(4).get("CLOSE_TIME")).isEqualTo(Time.valueOf("16:00:00"));
		assertThat(hours.get(4).get("CLOSED")).isEqualTo(false);

		assertThat(hours.get(5).get("DAY_OF_WEEK")).isEqualTo("SATURDAY");
		assertThat(hours.get(5).get("CLOSED")).isEqualTo(true);

		assertThat(hours.get(6).get("DAY_OF_WEEK")).isEqualTo("SUNDAY");
		assertThat(hours.get(6).get("CLOSED")).isEqualTo(true);
	}

	@Test
	void seededVetWeeklyBlocksContainExactly17Blocks() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vet_weekly_block", Integer.class);
		assertThat(count).isEqualTo(17);
	}

	@Test
	void seededVetExceptionsMatchExactValues() {
		List<Map<String, Object>> exceptions = jdbcTemplate
			.queryForList("SELECT vet_id, exception_date, unavailable FROM vet_exception ORDER BY id");

		assertThat(exceptions).hasSize(6);
		assertThat(exceptions.get(0).get("VET_ID")).isEqualTo(1);
		assertThat(LocalDate.parse(exceptions.get(0).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 9, 15));
		assertThat(exceptions.get(0).get("UNAVAILABLE")).isEqualTo(true);

		assertThat(exceptions.get(1).get("VET_ID")).isEqualTo(5);
		assertThat(LocalDate.parse(exceptions.get(1).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 9, 15));

		assertThat(exceptions.get(2).get("VET_ID")).isEqualTo(5);
		assertThat(LocalDate.parse(exceptions.get(2).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 9, 17));

		assertThat(exceptions.get(3).get("VET_ID")).isEqualTo(5);
		assertThat(LocalDate.parse(exceptions.get(3).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 9, 21));

		assertThat(exceptions.get(4).get("VET_ID")).isEqualTo(5);
		assertThat(LocalDate.parse(exceptions.get(4).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 10, 22));

		assertThat(exceptions.get(5).get("VET_ID")).isEqualTo(6);
		assertThat(LocalDate.parse(exceptions.get(5).get("EXCEPTION_DATE").toString()))
			.isEqualTo(LocalDate.of(2026, 10, 22));
	}

	@Test
	void noSeededLeaveOrClinicClosures() {
		Integer leaveCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vet_leave", Integer.class);
		Integer closureCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM clinic_closure", Integer.class);

		assertThat(leaveCount).isZero();
		assertThat(closureCount).isZero();
	}

	@Test
	void seededClinicConfigDefaultsMatchExactValues() {
		Map<String, Object> config = jdbcTemplate.queryForMap("SELECT * FROM clinic_config WHERE id = 1");

		assertThat(config.get("BOOKING_HORIZON_DAYS")).isEqualTo(30);
		assertThat(config.get("MIN_DURATION_MINUTES")).isEqualTo(15);
		assertThat(config.get("MAX_DURATION_MINUTES")).isEqualTo(60);
		assertThat(config.get("DEFAULT_DURATION_MINUTES")).isEqualTo(30);
		assertThat(config.get("GRID_INTERVAL_MINUTES")).isEqualTo(15);
		assertThat(config.get("TIME_ZONE")).isEqualTo("Europe/Amsterdam");
		assertThat(config.get("EMERGENCY_PHONE")).isNotNull();
	}

	@Test
	void activePetIdUniqueConstraintEnforced() {
		Timestamp now = Timestamp.from(Instant.now());

		jdbcTemplate.update(
				"INSERT INTO scheduling_request (id, pet_id, owner_id, state, reason_text, availability_text, active_pet_id, failed_attempts, created_at, updated_at) "
						+ "VALUES (1001, 1, 1, 'SUBMITTED', 'Checkup', 'Anytime', 1, 0, ?, ?)",
				now, now);

		// Inserting another active request for pet 1 must fail
		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO scheduling_request (id, pet_id, owner_id, state, reason_text, availability_text, active_pet_id, failed_attempts, created_at, updated_at) "
						+ "VALUES (1002, 1, 1, 'SUBMITTED', 'Followup', 'Morning', 1, 0, ?, ?)",
				now, now))
			.isInstanceOf(DataIntegrityViolationException.class);

		// Inserting completed request with active_pet_id NULL must succeed
		int rows = jdbcTemplate.update(
				"INSERT INTO scheduling_request (id, pet_id, owner_id, state, reason_text, availability_text, active_pet_id, failed_attempts, created_at, updated_at) "
						+ "VALUES (1003, 1, 1, 'CONFIRMED', 'Old visit', 'None', NULL, 0, ?, ?)",
				now, now);
		assertThat(rows).isEqualTo(1);
	}

	@Test
	void foreignKeyConstraintsEnforced() {
		Timestamp now = Timestamp.from(Instant.now());

		// Non-existent pet_id
		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO scheduling_request (id, pet_id, owner_id, state, reason_text, availability_text, active_pet_id, failed_attempts, created_at, updated_at) "
						+ "VALUES (2001, 99999, 1, 'SUBMITTED', 'Checkup', 'Anytime', 99999, 0, ?, ?)",
				now, now))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
