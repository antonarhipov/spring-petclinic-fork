package org.springframework.samples.petclinic.scheduling.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyDataPreservationAcceptanceTests {

	@Test
	@Timeout(20)
	void h2FreshMigrationHasPolicyAndReservationConstraints() throws Exception {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:legacy-fresh;MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
		Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/h2").load().migrate();
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			assertCatalogAndConstraints(connection, statement);
			assertThat(count(statement, "clinic_scheduling_policies")).isEqualTo(1);
			assertThat(count(statement, "allowed_durations")).isEqualTo(4);
			assertThat(count(statement, "owners")).isZero();
		}
	}

	@Test
	@Timeout(20)
	void h2BaselinedLegacyCountsArePreserved() throws Exception {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:legacy-base;MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
		loadLegacySchema(dataSource, "db/h2/schema.sql", "db/h2/data.sql");
		int owners;
		int pets;
		int vets;
		int specialties;
		int visits;
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			owners = count(statement, "owners");
			pets = count(statement, "pets");
			vets = count(statement, "vets");
			specialties = count(statement, "specialties");
			visits = count(statement, "visits");
		}
		Flyway.configure()
			.dataSource(dataSource)
			.locations("classpath:db/migration/h2")
			.baselineOnMigrate(true)
			.baselineVersion("1")
			.load()
			.migrate();
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			assertThat(count(statement, "owners")).isEqualTo(owners);
			assertThat(count(statement, "pets")).isEqualTo(pets);
			assertThat(count(statement, "vets")).isEqualTo(vets);
			assertThat(count(statement, "specialties")).isEqualTo(specialties);
			assertThat(count(statement, "visits")).isEqualTo(visits);
			assertCatalogAndConstraints(connection, statement);
			assertLegacyVisitUnlinked(statement);
		}
	}

	@Nested
	@Testcontainers(disabledWithoutDocker = true)
	class ForeignDatabases {

		@Test
		@Timeout(180)
		void mysqlFreshAndBaselinedCountsArePreserved() throws Exception {
			try (MySQLContainer fresh = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
				.withDatabaseName("t141_mysql_fresh")) {
				fresh.start();
				assertFresh(fresh.getJdbcUrl(), fresh.getUsername(), fresh.getPassword(),
						"classpath:db/migration/mysql");
			}
			try (MySQLContainer baseline = new MySQLContainer(DockerImageName.parse("mysql:9.7"))
				.withDatabaseName("t141_mysql_base")) {
				baseline.start();
				DriverManagerDataSource dataSource = new DriverManagerDataSource(baseline.getJdbcUrl(),
						baseline.getUsername(), baseline.getPassword());
				loadLegacySchema(dataSource, "db/mysql/schema.sql", "db/mysql/data.sql");
				assertBaselined(baseline.getJdbcUrl(), baseline.getUsername(), baseline.getPassword(),
						"classpath:db/migration/mysql");
			}
		}

		@Test
		@Timeout(180)
		void postgresFreshAndBaselinedCountsArePreserved() throws Exception {
			try (PostgreSQLContainer fresh = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
				.withDatabaseName("t141_pg_fresh")) {
				fresh.start();
				assertFresh(fresh.getJdbcUrl(), fresh.getUsername(), fresh.getPassword(),
						"classpath:db/migration/postgres");
			}
			try (PostgreSQLContainer baseline = new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
				.withDatabaseName("t141_pg_base")) {
				baseline.start();
				DriverManagerDataSource dataSource = new DriverManagerDataSource(baseline.getJdbcUrl(),
						baseline.getUsername(), baseline.getPassword());
				loadLegacySchema(dataSource, "db/postgres/schema.sql", "db/postgres/data.sql");
				assertBaselined(baseline.getJdbcUrl(), baseline.getUsername(), baseline.getPassword(),
						"classpath:db/migration/postgres");
			}
		}

	}

	private static void assertFresh(String url, String user, String password, String flywayLocation) throws Exception {
		Flyway.configure().dataSource(url, user, password).locations(flywayLocation).load().migrate();
		try (Connection connection = DriverManager.getConnection(url, user, password);
				Statement statement = connection.createStatement()) {
			assertThat(count(statement, "clinic_scheduling_policies")).isEqualTo(1);
			assertThat(count(statement, "allowed_durations")).isEqualTo(4);
			assertThat(count(statement, "owners")).isZero();
			assertCatalogAndConstraints(connection, statement);
		}
	}

	private static void assertBaselined(String url, String user, String password, String flywayLocation)
			throws Exception {
		int owners;
		int pets;
		int vets;
		int specialties;
		int visits;
		try (Connection connection = DriverManager.getConnection(url, user, password);
				Statement statement = connection.createStatement()) {
			owners = count(statement, "owners");
			pets = count(statement, "pets");
			vets = count(statement, "vets");
			specialties = count(statement, "specialties");
			visits = count(statement, "visits");
			assertThat(owners).isGreaterThan(0);
			assertThat(pets).isGreaterThan(0);
			assertThat(vets).isGreaterThan(0);
			assertThat(specialties).isGreaterThan(0);
			assertThat(visits).isGreaterThan(0);
		}
		Flyway.configure()
			.dataSource(url, user, password)
			.locations(flywayLocation)
			.baselineOnMigrate(true)
			.baselineVersion("1")
			.load()
			.migrate();
		try (Connection connection = DriverManager.getConnection(url, user, password);
				Statement statement = connection.createStatement()) {
			assertThat(count(statement, "owners")).isEqualTo(owners);
			assertThat(count(statement, "pets")).isEqualTo(pets);
			assertThat(count(statement, "vets")).isEqualTo(vets);
			assertThat(count(statement, "specialties")).isEqualTo(specialties);
			assertThat(count(statement, "visits")).isEqualTo(visits);
			assertCatalogAndConstraints(connection, statement);
			assertLegacyVisitUnlinked(statement);
		}
	}

	private static void loadLegacySchema(DataSource dataSource, String schemaPath, String dataPath) {
		ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
		populator.addScript(new ClassPathResource(schemaPath));
		populator.addScript(new ClassPathResource(dataPath));
		populator.execute(dataSource);
	}

	private static void assertCatalogAndConstraints(Connection connection, Statement statement) throws Exception {
		assertThat(count(statement, "reservation_blocks")).isZero();
		assertThat(count(statement, "accounts")).isZero();
		assertThat(hasPrimaryKey(connection, "reservation_blocks") || hasPrimaryKey(connection, "RESERVATION_BLOCKS"))
			.isTrue();
	}

	private static boolean hasPrimaryKey(Connection connection, String table) throws Exception {
		try (ResultSet rs = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, table)) {
			return rs.next();
		}
	}

	private static void assertLegacyVisitUnlinked(Statement statement) throws Exception {
		try (ResultSet rs = statement.executeQuery("select appointment_id from visits where id = 1")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getObject(1)).isNull();
		}
	}

	private static int count(Statement statement, String table) throws Exception {
		try (ResultSet rs = statement.executeQuery("select count(*) from " + table)) {
			rs.next();
			return rs.getInt(1);
		}
	}

}
