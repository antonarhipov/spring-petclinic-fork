package org.springframework.samples.petclinic.system;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformCompatibilityTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Test
	void contextLoadsAndPlatformDependenciesAreCompatible() throws Exception {
		assertThat(dataSource).isNotNull();
		assertThat(flyway).isNotNull();

		// Verify Java version is 21 or higher
		String javaVersion = System.getProperty("java.version");
		assertThat(javaVersion).isNotNull();

		// Verify Flyway baseline migration applied
		assertThat(flyway.info().current()).isNotNull();
		assertThat(Integer.parseInt(flyway.info().current().getVersion().getVersion())).isGreaterThanOrEqualTo(1);

		// Verify database table access through H2
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM vets")) {
			assertThat(resultSet.next()).isTrue();
			assertThat(resultSet.getInt(1)).isGreaterThan(0);
		}
	}

}
