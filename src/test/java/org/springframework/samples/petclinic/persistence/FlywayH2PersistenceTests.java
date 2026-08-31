package org.springframework.samples.petclinic.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayH2PersistenceTests {

	@Autowired
	private Flyway flyway;

	@Autowired
	private EntityManager entityManager;

	@Test
	void testHibernateValidationAndContextStartup() {
		assertThat(this.flyway).isNotNull();
		assertThat(this.entityManager).isNotNull();
		// If context started and ddl-auto=validate passed, schema matches JPA entities
		assertThat(this.entityManager.isOpen()).isTrue();
	}

	@Test
	void testFlywayMigrationIdempotency() {
		MigrateResult result = this.flyway.migrate();
		assertThat(result.migrationsExecuted).isEqualTo(0);
		assertThat(result.success).isTrue();
	}

	@Test
	void testFreshDatabaseMigrationAndRestartSurvivability(@TempDir Path tempDir) throws Exception {
		String dbPath = tempDir.resolve("testdb").toAbsolutePath().toString();
		String jdbcUrl = "jdbc:h2:file:" + dbPath + ";AUTO_SERVER=TRUE";

		// 1. Clean migration on fresh database
		Flyway fileFlyway = Flyway.configure().dataSource(jdbcUrl, "sa", "").locations("classpath:db/migration").load();

		MigrateResult migrateResult = fileFlyway.migrate();
		assertThat(migrateResult.success).isTrue();
		assertThat(migrateResult.migrationsExecuted).isGreaterThanOrEqualTo(2);

		// Insert test row into accounts table
		try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
				Statement stmt = conn.createStatement()) {
			stmt.executeUpdate(
					"INSERT INTO accounts (username, password_hash, role, owner_id, enabled, temporary_password_expires_at, password_change_required, session_version, version, created_at, updated_at) "
							+ "VALUES ('persist_test', 'hash', 'STAFF', NULL, true, NULL, false, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
		}

		// 2. Restart simulation: reconnect to file-backed DB and verify data
		// survivability
		try (Connection conn = DriverManager.getConnection(jdbcUrl, "sa", "");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt
					.executeQuery("SELECT username, role FROM accounts WHERE username = 'persist_test'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString("username")).isEqualTo("persist_test");
			assertThat(rs.getString("role")).isEqualTo("STAFF");
		}

		// 3. Re-run Flyway against existing DB (idempotent)
		MigrateResult secondMigrate = fileFlyway.migrate();
		assertThat(secondMigrate.success).isTrue();
		assertThat(secondMigrate.migrationsExecuted).isEqualTo(0);
	}

}
