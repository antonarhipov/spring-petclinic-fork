package org.springframework.samples.petclinic.support;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class PostgresContainerConfiguration {

	private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

	private PostgresContainerConfiguration() {
	}

	public static PostgreSQLContainer shared() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return CONTAINER;
	}

}
