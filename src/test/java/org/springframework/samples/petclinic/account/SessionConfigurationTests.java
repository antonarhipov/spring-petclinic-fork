package org.springframework.samples.petclinic.account;

import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SessionConfigurationTests {

	@Autowired
	Environment environment;

	@Autowired
	DataSource dataSource;

	@Test
	void sessionTimeoutIsThirtyMinutesAndPrincipalIndexExists() throws Exception {
		assertThat(this.environment.getProperty("spring.session.timeout")).isEqualTo("30m");
		try (Connection c = this.dataSource.getConnection();
				ResultSet rs = c.getMetaData().getIndexInfo(null, null, "SPRING_SESSION", false, false)) {
			boolean found = false;
			while (rs.next()) {
				String name = rs.getString("INDEX_NAME");
				if (name != null && name.contains("SPRING_SESSION_IX3")) {
					found = true;
				}
			}
			assertThat(found).isTrue();
		}
	}

}
