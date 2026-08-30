package org.springframework.samples.petclinic.support;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class MySqlContainerConfiguration {

	private static final MySQLContainer CONTAINER = new MySQLContainer(DockerImageName.parse("mysql:9.7"));

	private MySqlContainerConfiguration() {
	}

	public static MySQLContainer shared() {
		if (!CONTAINER.isRunning()) {
			CONTAINER.start();
		}
		return CONTAINER;
	}

}
