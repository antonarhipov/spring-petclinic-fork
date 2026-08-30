package org.springframework.samples.petclinic.scheduling.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class MySqlSchedulingMigrationTests {

	@Test
	void freshSchemaAppliesV1AndV2() throws Exception {
		try (MySQLContainer container = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
			.withDatabaseName("fresh_sched")) {
			container.start();
			Flyway.configure()
				.dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
				.locations("classpath:db/migration/mysql")
				.load()
				.migrate();
			try (Connection c = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(),
					container.getPassword());
					Statement s = c.createStatement();
					ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM clinic_scheduling_policies")) {
				rs.next();
				assertThat(rs.getInt(1)).isEqualTo(1);
			}
		}
	}

	@Test
	void baselineOnMigratePreservesLegacyRows() throws Exception {
		try (MySQLContainer container = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
			.withDatabaseName("legacy_sched")) {
			container.start();
			String url = container.getJdbcUrl();
			String user = container.getUsername();
			String password = container.getPassword();
			Flyway.configure()
				.dataSource(url, user, password)
				.locations("classpath:db/migration/mysql")
				.target("1")
				.load()
				.migrate();
			try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
				s.execute(
						"INSERT INTO owners (first_name, last_name, address, city, telephone) VALUES ('A','B','C','D','1')");
			}
			Flyway.configure()
				.dataSource(url, user, password)
				.locations("classpath:db/migration/mysql")
				.load()
				.migrate();
			try (Connection c = DriverManager.getConnection(url, user, password);
					Statement s = c.createStatement();
					ResultSet owners = s.executeQuery("SELECT COUNT(*) FROM owners")) {
				owners.next();
				assertThat(owners.getInt(1)).isEqualTo(1);
			}
			try (Connection c = DriverManager.getConnection(url, user, password);
					Statement s = c.createStatement();
					ResultSet policies = s.executeQuery("SELECT COUNT(*) FROM clinic_scheduling_policies")) {
				policies.next();
				assertThat(policies.getInt(1)).isEqualTo(1);
			}
		}
	}

}
