package org.springframework.samples.petclinic.scheduling;

import org.springframework.boot.test.context.TestConfiguration;

/**
 * Marker configuration for database-specific scheduling suites. Those suites use the
 * repository's existing MySQL and PostgreSQL Testcontainers/Docker Compose support.
 */
@TestConfiguration
public class TestcontainersConfiguration {

}
