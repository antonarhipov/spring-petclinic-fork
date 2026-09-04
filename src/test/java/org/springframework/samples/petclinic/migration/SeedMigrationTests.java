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

package org.springframework.samples.petclinic.migration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs V1 through V4 against a new in-memory database and proves every normative seed row
 * by value and as a closed set (RULE-42).
 */
class SeedMigrationTests {

	private static JdbcTemplate jdbc;

	private static String databaseUrl;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@BeforeAll
	static void migrateFreshDatabase() {
		databaseUrl = "jdbc:h2:mem:seed-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
		DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, "sa", "");
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void usesOnlyAnIsolatedInMemoryDatabase() {
		assertThat(databaseUrl).startsWith("jdbc:h2:mem:seed-migration-");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" AND \"version\" IS NOT NULL",
				Integer.class))
			.isEqualTo(4);
	}

	@Test
	void seedsExactlyTheNormativeAccountsAndRoles() {
		List<AccountSeed> actual = jdbc.query(
				"SELECT u.username, u.password, u.role, o.first_name, o.last_name FROM users u LEFT JOIN owners o ON o.id = u.owner_id ORDER BY u.id",
				(rs, row) -> new AccountSeed(rs.getString("username"), rs.getString("password"), rs.getString("role"),
						rs.getString("first_name"), rs.getString("last_name")));

		List<AccountExpected> expected = List.of(
				new AccountExpected("george", "george123", "owner", "George", "Franklin"),
				new AccountExpected("betty", "betty123", "owner", "Betty", "Davis"),
				new AccountExpected("eduardo", "eduardo123", "owner", "Eduardo", "Rodriquez"),
				new AccountExpected("harold", "harold123", "owner", "Harold", "Davis"),
				new AccountExpected("peter", "peter123", "owner", "Peter", "McTavish"),
				new AccountExpected("jean", "jean123", "owner", "Jean", "Coleman"),
				new AccountExpected("jeff", "jeff123", "owner", "Jeff", "Black"),
				new AccountExpected("maria", "maria123", "owner", "Maria", "Escobito"),
				new AccountExpected("david", "david123", "owner", "David", "Schroeder"),
				new AccountExpected("carlos", "carlos123", "owner", "Carlos", "Estaban"),
				new AccountExpected("admin", "admin123", "staff", null, null),
				new AccountExpected("staff", "staff123", "staff", null, null));

		assertThat(actual).hasSameSizeAs(expected);
		for (int index = 0; index < expected.size(); index++) {
			AccountExpected expectedAccount = expected.get(index);
			AccountSeed actualAccount = actual.get(index);
			assertThat(actualAccount.username()).isEqualTo(expectedAccount.username());
			assertThat(actualAccount.role()).isEqualTo(expectedAccount.role());
			assertThat(actualAccount.firstName()).isEqualTo(expectedAccount.firstName());
			assertThat(actualAccount.lastName()).isEqualTo(expectedAccount.lastName());
			assertThat(this.passwordEncoder.matches(expectedAccount.rawPassword(), actualAccount.passwordHash()))
				.isTrue();
		}
		assertThat(jdbc.queryForList("SELECT DISTINCT role FROM users ORDER BY role", String.class))
			.containsExactly("owner", "staff");
	}

	@Test
	void seedsExactlyClinicAOpeningHours() {
		List<OpeningHour> actual = jdbc.query(
				"SELECT clinic_name, day_of_week, open_time, close_time, closed FROM clinic_opening_hour ORDER BY id",
				(rs, row) -> new OpeningHour(rs.getString("clinic_name"), rs.getString("day_of_week"),
						rs.getTime("open_time") == null ? null : rs.getTime("open_time").toLocalTime(),
						rs.getTime("close_time") == null ? null : rs.getTime("close_time").toLocalTime(),
						rs.getBoolean("closed")));

		assertThat(actual).containsExactly(new OpeningHour("Clinic A", "MONDAY", time(9), time(17), false),
				new OpeningHour("Clinic A", "TUESDAY", time(9), time(17), false),
				new OpeningHour("Clinic A", "WEDNESDAY", time(9), time(18), false),
				new OpeningHour("Clinic A", "THURSDAY", time(9), time(17), false),
				new OpeningHour("Clinic A", "FRIDAY", time(10), time(16), false),
				new OpeningHour("Clinic A", "SATURDAY", null, null, true),
				new OpeningHour("Clinic A", "SUNDAY", null, null, true));
	}

	@Test
	void seedsExactlyAllVetWeeklyBlocks() {
		List<VetBlock> actual = jdbc.query(
				"SELECT CONCAT(v.first_name, ' ', v.last_name) vet_name, b.day_of_week, b.start_time, b.end_time FROM vet_weekly_block b JOIN vets v ON v.id = b.vet_id ORDER BY b.id",
				(rs, row) -> new VetBlock(rs.getString("vet_name"), rs.getString("day_of_week"),
						rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime()));

		assertThat(actual).containsExactly(block("James Carter", "MONDAY", 9, 17),
				block("James Carter", "TUESDAY", 9, 17), block("James Carter", "WEDNESDAY", 9, 12),
				block("James Carter", "FRIDAY", 11, 12), block("Helen Leary", "MONDAY", 9, 17),
				block("Helen Leary", "TUESDAY", 9, 17), block("Helen Leary", "WEDNESDAY", 9, 12),
				block("Linda Douglas", "MONDAY", 9, 17), block("Linda Douglas", "TUESDAY", 9, 17),
				block("Linda Douglas", "WEDNESDAY", 9, 12), block("Rafael Ortega", "THURSDAY", 9, 17),
				block("Rafael Ortega", "FRIDAY", 10, 16), block("Henry Stevens", "THURSDAY", 9, 17),
				block("Henry Stevens", "FRIDAY", 10, 16), block("Sharon Jenkins", "MONDAY", 13, 14),
				block("Sharon Jenkins", "THURSDAY", 9, 17), block("Sharon Jenkins", "FRIDAY", 10, 16));
	}

	@Test
	void seedsExactlyAllUnavailableExceptionsAndNoLeaveOrClosures() {
		List<VetException> actual = jdbc.query(
				"SELECT CONCAT(v.first_name, ' ', v.last_name) vet_name, e.exception_date, e.unavailable FROM vet_exception e JOIN vets v ON v.id = e.vet_id ORDER BY e.id",
				(rs, row) -> new VetException(rs.getString("vet_name"), rs.getObject("exception_date", LocalDate.class),
						rs.getBoolean("unavailable")));

		assertThat(actual).containsExactly(new VetException("James Carter", date(2026, 9, 15), true),
				new VetException("Henry Stevens", date(2026, 9, 15), true),
				new VetException("Henry Stevens", date(2026, 9, 17), true),
				new VetException("Henry Stevens", date(2026, 9, 21), true),
				new VetException("Henry Stevens", date(2026, 10, 22), true),
				new VetException("Sharon Jenkins", date(2026, 10, 22), true));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vet_leave", Integer.class)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinic_closure", Integer.class)).isZero();
	}

	@Test
	void seedsExactlyAllPartsOfDayAndTheSingleConfigRow() {
		List<PartOfDay> parts = jdbc.query("SELECT name, start_time, end_time FROM clinic_part_of_day ORDER BY id",
				(rs, row) -> new PartOfDay(rs.getString("name"), rs.getTime("start_time").toLocalTime(),
						rs.getTime("end_time").toLocalTime()));
		assertThat(parts).containsExactly(new PartOfDay("morning", time(9), time(12)),
				new PartOfDay("afternoon", time(12), time(17)), new PartOfDay("evening", time(17), time(18)));

		List<ClinicConfig> configs = jdbc.query(
				"SELECT booking_horizon_days, min_duration_minutes, max_duration_minutes, default_duration_minutes, grid_interval_minutes, time_zone, emergency_phone FROM clinic_config ORDER BY id",
				(rs, row) -> new ClinicConfig(rs.getInt("booking_horizon_days"), rs.getInt("min_duration_minutes"),
						rs.getInt("max_duration_minutes"), rs.getInt("default_duration_minutes"),
						rs.getInt("grid_interval_minutes"), rs.getString("time_zone"),
						rs.getString("emergency_phone")));
		assertThat(configs).containsExactly(new ClinicConfig(30, 15, 60, 30, 15, "Europe/Amsterdam", "555-0199"));
	}

	private static LocalTime time(int hour) {
		return LocalTime.of(hour, 0);
	}

	private static LocalDate date(int year, int month, int day) {
		return LocalDate.of(year, month, day);
	}

	private static VetBlock block(String vet, String day, int start, int end) {
		return new VetBlock(vet, day, time(start), time(end));
	}

	private record AccountSeed(String username, String passwordHash, String role, String firstName, String lastName) {
	}

	private record AccountExpected(String username, String rawPassword, String role, String firstName,
			String lastName) {
	}

	private record OpeningHour(String clinic, String day, LocalTime open, LocalTime close, boolean closed) {
	}

	private record VetBlock(String vet, String day, LocalTime start, LocalTime end) {
	}

	private record VetException(String vet, LocalDate date, boolean unavailable) {
	}

	private record PartOfDay(String name, LocalTime start, LocalTime end) {
	}

	private record ClinicConfig(int horizon, int minDuration, int maxDuration, int defaultDuration, int grid,
			String zone, String emergencyPhone) {
	}

}
