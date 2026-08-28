package org.springframework.samples.petclinic.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.samples.petclinic.PetClinicApplication;
import javax.sql.DataSource;

@SpringBootTest(classes = PetClinicApplication.class)
class FlywayMigrationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void migratesBaselineAndSchedulingSchema() throws Exception {
		try (var connection = this.dataSource.getConnection(); var statement = connection.createStatement()) {
			assertThat(count(statement, "\"flyway_schema_history\"")).isGreaterThanOrEqualTo(2);
			assertThat(count(statement, "owners")).isEqualTo(10);
			assertThat(count(statement, "clinic_scheduling_settings")).isEqualTo(1);
			assertThat(count(statement, "recurring_vet_shifts")).isEqualTo(30);
			assertThat(count(statement, "accounts")).isGreaterThanOrEqualTo(11);
		}
	}

	private int count(java.sql.Statement statement, String table) throws java.sql.SQLException {
		try (var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rows.next();
			return rows.getInt(1);
		}
	}

}
