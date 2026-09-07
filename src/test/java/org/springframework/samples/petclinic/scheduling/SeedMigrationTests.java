/*
 * Copyright 2012-2025 the original author or authors.
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
package org.springframework.samples.petclinic.scheduling;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SeedMigrationTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Tag("AC-122")
	@Transactional
	void ac122_vets_and_specialties_every_row_by_value() {
		assertV1WasAppliedByFlyway();

		List<SpecialtySeed> specialties = this.jdbcTemplate.query("select id, name from specialties order by id",
				(result, row) -> new SpecialtySeed(result.getInt("id"), result.getString("name")));
		assertThat(specialties).containsExactly(new SpecialtySeed(1, "radiology"), new SpecialtySeed(2, "surgery"),
				new SpecialtySeed(3, "dentistry"));

		List<VetSeed> vets = this.jdbcTemplate.query("""
				select v.id, v.first_name, v.last_name, s.name as specialty
				from vets v
				left join vet_specialties vs on vs.vet_id = v.id
				left join specialties s on s.id = vs.specialty_id
				order by v.id, s.name
				""", (result, row) -> new VetSeed(result.getInt("id"), result.getString("first_name"),
				result.getString("last_name"), result.getString("specialty")));
		assertThat(vets).containsExactly(new VetSeed(1, "James", "Carter", null),
				new VetSeed(2, "Helen", "Leary", "radiology"), new VetSeed(3, "Linda", "Douglas", "dentistry"),
				new VetSeed(3, "Linda", "Douglas", "surgery"), new VetSeed(4, "Rafael", "Ortega", "surgery"),
				new VetSeed(5, "Henry", "Stevens", "radiology"), new VetSeed(6, "Sharon", "Jenkins", null));

		String checkClause = this.jdbcTemplate.queryForObject("""
				select check_clause from information_schema.check_constraints
				where constraint_name = 'CK_INTERPRETATIONS_SPECIALTY'
				""", String.class);
		List<String> constrainedValues = Pattern.compile("'([^']*)'")
			.matcher(checkClause)
			.results()
			.map(result -> result.group(1))
			.toList();
		assertThat(constrainedValues).containsExactlyInAnyOrder("radiology", "surgery", "dentistry", "OTHER");

		this.jdbcTemplate.update("""
				insert into scheduling_requests
				(id, pet_id, request_text, state, created_date, created_time, version)
				values (9000, 1, 'constraint proof', 'INTERPRETING', DATE '2026-09-06', TIME '10:00:00', 0)
				""");
		for (String specialty : List.of("radiology", "surgery", "dentistry", "OTHER")) {
			this.jdbcTemplate.update("""
					insert into interpretations
					(request_id, understood, care_type, specialty, origin, created_date, created_time)
					values (9000, true, 'SPECIALTY', ?, 'AI', DATE '2026-09-06', TIME '10:00:00')
					""", specialty);
		}
		assertThat(this.jdbcTemplate.queryForList(
				"select specialty from interpretations where request_id = 9000 order by specialty", String.class))
			.containsExactly("OTHER", "dentistry", "radiology", "surgery");
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				insert into interpretations
				(request_id, understood, care_type, specialty, origin, created_date, created_time)
				values (9000, true, 'SPECIALTY', 'cardiology', 'AI', DATE '2026-09-06', TIME '10:00:00')
				""")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@Tag("AC-123")
	void ac123_owners_pets_and_types_every_row_by_value() {
		assertV1WasAppliedByFlyway();

		List<OwnerSeed> owners = this.jdbcTemplate.query("""
				select id, first_name, last_name, address, city, telephone
				from owners order by id
				""",
				(result, row) -> new OwnerSeed(result.getInt("id"), result.getString("first_name"),
						result.getString("last_name"), result.getString("address"), result.getString("city"),
						result.getString("telephone")));
		assertThat(owners).containsExactly(
				new OwnerSeed(1, "George", "Franklin", "110 W. Liberty St.", "Madison", "6085551023"),
				new OwnerSeed(2, "Betty", "Davis", "638 Cardinal Ave.", "Sun Prairie", "6085551749"),
				new OwnerSeed(3, "Eduardo", "Rodriquez", "2693 Commerce St.", "McFarland", "6085558763"),
				new OwnerSeed(4, "Harold", "Davis", "563 Friendly St.", "Windsor", "6085553198"),
				new OwnerSeed(5, "Peter", "McTavish", "2387 S. Fair Way", "Madison", "6085552765"),
				new OwnerSeed(6, "Jean", "Coleman", "105 N. Lake St.", "Monona", "6085552654"),
				new OwnerSeed(7, "Jeff", "Black", "1450 Oak Blvd.", "Monona", "6085555387"),
				new OwnerSeed(8, "Maria", "Escobito", "345 Maple St.", "Madison", "6085557683"),
				new OwnerSeed(9, "David", "Schroeder", "2749 Blackhawk Trail", "Madison", "6085559435"),
				new OwnerSeed(10, "Carlos", "Estaban", "2335 Independence La.", "Waunakee", "6085555487"));

		List<PetTypeSeed> types = this.jdbcTemplate.query("select id, name from types order by id",
				(result, row) -> new PetTypeSeed(result.getInt("id"), result.getString("name")));
		assertThat(types).containsExactly(new PetTypeSeed(1, "cat"), new PetTypeSeed(2, "dog"),
				new PetTypeSeed(3, "lizard"), new PetTypeSeed(4, "snake"), new PetTypeSeed(5, "bird"),
				new PetTypeSeed(6, "hamster"));

		List<PetSeed> pets = this.jdbcTemplate.query("""
				select p.id, p.name, p.birth_date, p.owner_id, t.name as type
				from pets p join types t on t.id = p.type_id
				order by p.id
				""", (result, row) -> new PetSeed(result.getInt("id"), result.getString("name"),
				result.getObject("birth_date", LocalDate.class), result.getInt("owner_id"), result.getString("type")));
		assertThat(pets).containsExactly(new PetSeed(1, "Leo", LocalDate.of(2010, 9, 7), 1, "cat"),
				new PetSeed(2, "Basil", LocalDate.of(2012, 8, 6), 2, "hamster"),
				new PetSeed(3, "Rosy", LocalDate.of(2011, 4, 17), 3, "dog"),
				new PetSeed(4, "Jewel", LocalDate.of(2010, 3, 7), 3, "dog"),
				new PetSeed(5, "Iggy", LocalDate.of(2010, 11, 30), 4, "lizard"),
				new PetSeed(6, "George", LocalDate.of(2010, 1, 20), 5, "snake"),
				new PetSeed(7, "Samantha", LocalDate.of(2012, 9, 4), 6, "cat"),
				new PetSeed(8, "Max", LocalDate.of(2012, 9, 4), 6, "cat"),
				new PetSeed(9, "Lucky", LocalDate.of(2011, 8, 6), 7, "bird"),
				new PetSeed(10, "Mulligan", LocalDate.of(2007, 2, 24), 8, "dog"),
				new PetSeed(11, "Freddy", LocalDate.of(2010, 3, 9), 9, "bird"),
				new PetSeed(12, "Lucky", LocalDate.of(2010, 6, 24), 10, "dog"),
				new PetSeed(13, "Sly", LocalDate.of(2012, 6, 8), 10, "cat"));
	}

	@Test
	@Tag("AC-124")
	void ac124_stock_visits_every_row_by_value_and_unlinked() {
		List<VisitSeed> visits = this.jdbcTemplate.query("""
				select visits.id, pets.name as pet_name, visits.visit_date, visits.description, visits.appointment_id
				from visits join pets on pets.id = visits.pet_id
				order by visits.id
				""",
				(result, row) -> new VisitSeed(result.getInt("id"), result.getString("pet_name"),
						result.getObject("visit_date", LocalDate.class), result.getString("description"),
						result.getObject("appointment_id", Integer.class)));

		assertThat(visits).containsExactly(new VisitSeed(1, "Samantha", LocalDate.of(2013, 1, 1), "rabies shot", null),
				new VisitSeed(2, "Max", LocalDate.of(2013, 1, 2), "rabies shot", null),
				new VisitSeed(3, "Max", LocalDate.of(2013, 1, 3), "neutered", null),
				new VisitSeed(4, "Samantha", LocalDate.of(2013, 1, 4), "spayed", null));
	}

	@Test
	@Tag("AC-120")
	void ac120_accounts_every_row_by_value() {
		assertMigrationWasApplied("3");

		List<AccountSeed> accounts = this.jdbcTemplate.query("""
				select a.username, a.role, o.first_name, o.last_name
				from user_accounts a
				left join owners o on o.id = a.owner_id
				order by a.username
				""", (result, row) -> new AccountSeed(result.getString("username"), result.getString("role"),
				result.getString("first_name"), result.getString("last_name")));

		assertThat(accounts).containsExactly(new AccountSeed("admin", "STAFF", null, null),
				new AccountSeed("betty", "OWNER", "Betty", "Davis"),
				new AccountSeed("carlos", "OWNER", "Carlos", "Estaban"),
				new AccountSeed("david", "OWNER", "David", "Schroeder"),
				new AccountSeed("eduardo", "OWNER", "Eduardo", "Rodriquez"),
				new AccountSeed("george", "OWNER", "George", "Franklin"),
				new AccountSeed("harold", "OWNER", "Harold", "Davis"),
				new AccountSeed("jean", "OWNER", "Jean", "Coleman"), new AccountSeed("jeff", "OWNER", "Jeff", "Black"),
				new AccountSeed("maria", "OWNER", "Maria", "Escobito"),
				new AccountSeed("peter", "OWNER", "Peter", "McTavish"), new AccountSeed("staff", "STAFF", null, null));
	}

	@Test
	@Tag("AC-121")
	void ac121_every_password_matches_encoder() {
		PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		List<CredentialSeed> credentials = this.jdbcTemplate.query("""
				select username, password_hash from user_accounts order by username
				""",
				(result, row) -> new CredentialSeed(result.getString("username"), result.getString("password_hash")));

		assertThat(credentials).hasSize(12).allSatisfy(credential -> {
			assertThat(credential.passwordHash()).startsWith("$2");
			assertThat(passwordEncoder.matches(credential.username() + "123", credential.passwordHash())).isTrue();
		});
	}

	@Test
	@Tag("AC-125")
	void ac125_opening_hours_every_weekday_by_value() {
		assertMigrationWasApplied("4");
		List<OpeningHoursSeed> hours = this.jdbcTemplate.query("""
				select weekday, open_time, close_time from clinic_opening_hours order by id
				""", (result, row) -> new OpeningHoursSeed(result.getString("weekday"),
				result.getObject("open_time", LocalTime.class), result.getObject("close_time", LocalTime.class)));

		assertThat(hours).containsExactly(new OpeningHoursSeed("MONDAY", LocalTime.of(9, 0), LocalTime.of(17, 0)),
				new OpeningHoursSeed("TUESDAY", LocalTime.of(9, 0), LocalTime.of(17, 0)),
				new OpeningHoursSeed("WEDNESDAY", LocalTime.of(9, 0), LocalTime.of(18, 0)),
				new OpeningHoursSeed("THURSDAY", LocalTime.of(9, 0), LocalTime.of(17, 0)),
				new OpeningHoursSeed("FRIDAY", LocalTime.of(10, 0), LocalTime.of(16, 0)),
				new OpeningHoursSeed("SATURDAY", null, null), new OpeningHoursSeed("SUNDAY", null, null));
	}

	@Test
	@Tag("AC-126")
	void ac126_all_17_working_blocks_by_value() {
		List<WorkingBlockSeed> blocks = this.jdbcTemplate.query("""
				select v.first_name, v.last_name, b.weekday, b.start_time, b.end_time
				from vet_working_blocks b join vets v on v.id = b.vet_id
				order by b.id
				""",
				(result, row) -> new WorkingBlockSeed(result.getString("first_name"), result.getString("last_name"),
						result.getString("weekday"), result.getObject("start_time", LocalTime.class),
						result.getObject("end_time", LocalTime.class)));

		assertThat(blocks).containsExactly(block("James", "Carter", "MONDAY", 9, 17),
				block("James", "Carter", "TUESDAY", 9, 17), block("James", "Carter", "WEDNESDAY", 9, 12),
				block("James", "Carter", "FRIDAY", 11, 12), block("Helen", "Leary", "MONDAY", 9, 17),
				block("Helen", "Leary", "TUESDAY", 9, 17), block("Helen", "Leary", "WEDNESDAY", 9, 12),
				block("Linda", "Douglas", "MONDAY", 9, 17), block("Linda", "Douglas", "TUESDAY", 9, 17),
				block("Linda", "Douglas", "WEDNESDAY", 9, 12), block("Rafael", "Ortega", "THURSDAY", 9, 17),
				block("Rafael", "Ortega", "FRIDAY", 10, 16), block("Henry", "Stevens", "THURSDAY", 9, 17),
				block("Henry", "Stevens", "FRIDAY", 10, 16), block("Sharon", "Jenkins", "MONDAY", 13, 14),
				block("Sharon", "Jenkins", "THURSDAY", 9, 17), block("Sharon", "Jenkins", "FRIDAY", 10, 16));
	}

	@Test
	@Tag("AC-127")
	void ac127_six_exceptions_and_no_leave_or_closure_by_value() {
		List<ExceptionSeed> exceptions = this.jdbcTemplate.query("""
				select v.first_name, v.last_name, e.exception_date
				from vet_exceptions e join vets v on v.id = e.vet_id
				order by e.id
				""", (result, row) -> new ExceptionSeed(result.getString("first_name"), result.getString("last_name"),
				result.getObject("exception_date", LocalDate.class)));

		assertThat(exceptions).containsExactly(new ExceptionSeed("James", "Carter", LocalDate.of(2026, 9, 15)),
				new ExceptionSeed("Henry", "Stevens", LocalDate.of(2026, 9, 15)),
				new ExceptionSeed("Henry", "Stevens", LocalDate.of(2026, 9, 17)),
				new ExceptionSeed("Henry", "Stevens", LocalDate.of(2026, 9, 21)),
				new ExceptionSeed("Henry", "Stevens", LocalDate.of(2026, 10, 22)),
				new ExceptionSeed("Sharon", "Jenkins", LocalDate.of(2026, 10, 22)));
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from vet_leave", Integer.class)).isZero();
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from clinic_closures", Integer.class)).isZero();
	}

	@Test
	@Tag("AC-128")
	void ac128_configuration_defaults_every_value() {
		List<SettingsSeed> settings = this.jdbcTemplate.query("""
				select booking_horizon_days, minimum_lead_days, minimum_duration_minutes,
				       default_duration_minutes, maximum_duration_minutes, grid_minutes,
				       morning_start, morning_end, afternoon_start, afternoon_end,
				       evening_start, evening_end, time_zone
				from clinic_settings order by id
				""", (result, row) -> new SettingsSeed(result.getInt("booking_horizon_days"),
				result.getInt("minimum_lead_days"), result.getInt("minimum_duration_minutes"),
				result.getInt("default_duration_minutes"), result.getInt("maximum_duration_minutes"),
				result.getInt("grid_minutes"), result.getObject("morning_start", LocalTime.class),
				result.getObject("morning_end", LocalTime.class), result.getObject("afternoon_start", LocalTime.class),
				result.getObject("afternoon_end", LocalTime.class), result.getObject("evening_start", LocalTime.class),
				result.getObject("evening_end", LocalTime.class), result.getString("time_zone")));

		assertThat(settings).containsExactly(
				new SettingsSeed(30, 1, 15, 30, 60, 15, LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(12, 0),
						LocalTime.of(17, 0), LocalTime.of(17, 0), LocalTime.of(18, 0), "Europe/Amsterdam"));
	}

	private WorkingBlockSeed block(String firstName, String lastName, String weekday, int startHour, int endHour) {
		return new WorkingBlockSeed(firstName, lastName, weekday, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
	}

	private void assertV1WasAppliedByFlyway() {
		assertMigrationWasApplied("1");
	}

	private void assertMigrationWasApplied(String version) {
		assertThat(this.jdbcTemplate.queryForObject(
				"select count(*) from \"flyway_schema_history\" where \"version\" = ? and \"success\" = true",
				Integer.class, version))
			.isEqualTo(1);
	}

	private record SpecialtySeed(int id, String name) {
	}

	private record VetSeed(int id, String firstName, String lastName, String specialty) {
	}

	private record OwnerSeed(int id, String firstName, String lastName, String address, String city, String telephone) {
	}

	private record PetTypeSeed(int id, String name) {
	}

	private record PetSeed(int id, String name, LocalDate birthDate, int ownerId, String type) {
	}

	private record VisitSeed(int id, String petName, LocalDate date, String description, Integer appointmentId) {
	}

	private record AccountSeed(String username, String role, String ownerFirstName, String ownerLastName) {
	}

	private record CredentialSeed(String username, String passwordHash) {
	}

	private record OpeningHoursSeed(String weekday, LocalTime openTime, LocalTime closeTime) {
	}

	private record WorkingBlockSeed(String firstName, String lastName, String weekday, LocalTime startTime,
			LocalTime endTime) {
	}

	private record ExceptionSeed(String firstName, String lastName, LocalDate date) {
	}

	private record SettingsSeed(int horizon, int lead, int minimumDuration, int defaultDuration, int maximumDuration,
			int grid, LocalTime morningStart, LocalTime morningEnd, LocalTime afternoonStart, LocalTime afternoonEnd,
			LocalTime eveningStart, LocalTime eveningEnd, String timeZone) {
	}

}
