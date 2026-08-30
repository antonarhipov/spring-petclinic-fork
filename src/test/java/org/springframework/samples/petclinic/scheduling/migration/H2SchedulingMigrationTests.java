package org.springframework.samples.petclinic.scheduling.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class H2SchedulingMigrationTests {

	@Test
	void freshSchemaAppliesLegacyAndSchedulingMigrations() throws Exception {
		String url = jdbcUrl("fresh");
		Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration/h2").load().migrate();
		try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
			assertThat(tableExists(s, "OWNERS")).isTrue();
			assertThat(tableExists(s, "ACCOUNTS")).isTrue();
			assertThat(tableExists(s, "RESERVATION_BLOCKS")).isTrue();
			try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM clinic_scheduling_policies")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(1);
			}
			try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM allowed_durations")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(4);
			}
		}
	}

	@Test
	void legacyRowsArePreservedWhenAdoptingFlyway() throws Exception {
		String url = jdbcUrl("legacy");
		try (Connection ignored = DriverManager.getConnection(
				url + ";INIT=RUNSCRIPT FROM 'classpath:db/h2/schema.sql'\\;RUNSCRIPT FROM 'classpath:db/h2/data.sql'",
				"sa", "")) {
			// load legacy schema/data once
		}
		int ownersBefore;
		try (Connection c = DriverManager.getConnection(url, "sa", "");
				Statement s = c.createStatement();
				ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM owners")) {
			rs.next();
			ownersBefore = rs.getInt(1);
		}
		Flyway.configure()
			.dataSource(url, "sa", "")
			.locations("classpath:db/migration/h2")
			.baselineOnMigrate(true)
			.baselineVersion("1")
			.load()
			.migrate();
		try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement s = c.createStatement()) {
			try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM owners")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(ownersBefore);
			}
			assertThat(tableExists(s, "ACCOUNTS")).isTrue();
			try (ResultSet rs = s.executeQuery("SELECT appointment_id FROM visits WHERE id=1")) {
				assertThat(rs.next()).isTrue();
				assertThat(rs.getObject(1)).isNull();
			}
		}
	}

	private static String jdbcUrl(String name) {
		return "jdbc:h2:mem:migration-" + name + ";DB_CLOSE_DELAY=-1";
	}

	private static boolean tableExists(Statement s, String name) throws Exception {
		try (ResultSet rs = s
			.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='" + name + "'")) {
			rs.next();
			return rs.getInt(1) > 0;
		}
	}

}
