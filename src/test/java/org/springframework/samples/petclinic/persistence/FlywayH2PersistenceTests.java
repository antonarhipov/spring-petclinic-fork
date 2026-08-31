package org.springframework.samples.petclinic.persistence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
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
		String jdbcUrl = "jdbc:h2:file:" + dbPath;

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
			stmt.executeUpdate(
					"INSERT INTO background_jobs (job_type, state, run_sequence, attempt_count, calendar_retry_count, lease_token, lease_until, available_at, version) "
							+ "VALUES ('INTERPRETATION', 'RUNNING', 1, 1, 0, 'restart-lease', DATEADD('MINUTE', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, 0)");
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
			try (ResultSet job = stmt.executeQuery(
					"SELECT state, lease_token, attempt_count FROM background_jobs WHERE lease_token = 'restart-lease'")) {
				assertThat(job.next()).isTrue();
				assertThat(job.getString("state")).isEqualTo("RUNNING");
				assertThat(job.getInt("attempt_count")).isEqualTo(1);
			}
		}

		// 3. Re-run Flyway against existing DB (idempotent)
		MigrateResult secondMigrate = fileFlyway.migrate();
		assertThat(secondMigrate.success).isTrue();
		assertThat(secondMigrate.migrationsExecuted).isEqualTo(0);
	}

	@Test
	void fileBackedDatabaseRejectsASecondApplicationProcess(@TempDir Path tempDir) throws Exception {
		String jdbcUrl = "jdbc:h2:file:" + tempDir.resolve("exclusive-db").toAbsolutePath();
		try (Connection ignored = DriverManager.getConnection(jdbcUrl, "sa", "")) {
			Process blocked = h2Shell(jdbcUrl).start();
			assertThat(blocked.waitFor(10, TimeUnit.SECONDS)).isTrue();
			assertThat(blocked.exitValue()).isNotZero();
			assertThat(new String(blocked.getInputStream().readAllBytes()))
				.containsAnyOf("Database may be already in use", "file is locked");
		}

		Process afterRelease = h2Shell(jdbcUrl).start();
		assertThat(afterRelease.waitFor(10, TimeUnit.SECONDS)).isTrue();
		assertThat(new String(afterRelease.getInputStream().readAllBytes())).contains("1");
		assertThat(afterRelease.exitValue()).isZero();
	}

	private ProcessBuilder h2Shell(String jdbcUrl) {
		String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		return new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), "org.h2.tools.Shell",
				"-url", jdbcUrl, "-user", "sa", "-password", "", "-sql", "SELECT 1")
			.redirectErrorStream(true);
	}

}
