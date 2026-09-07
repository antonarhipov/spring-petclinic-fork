/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.scheduling.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TestDataIsolationTests {

	private static final Path RUNTIME_DATA = Path.of("data");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@Tag("AC-134")
	void ac134_tests_use_unique_in_memory_h2() throws Exception {
		try (Connection connection = this.dataSource.getConnection()) {
			String url = connection.getMetaData().getURL();
			assertThat(url).startsWith("jdbc:h2:mem:petclinic-")
				.doesNotContain("./data")
				.matches("jdbc:h2:mem:petclinic-[0-9a-f-]{36}");
		}
	}

	@Test
	@Tag("AC-134")
	void ac134_runtime_data_directory_is_untouched() throws IOException {
		List<PathMetadata> before = snapshot(RUNTIME_DATA);
		assertThat(this.jdbcTemplate.queryForObject("select count(*) from owners", Integer.class)).isPositive();
		assertThat(snapshot(RUNTIME_DATA)).containsExactlyElementsOf(before);
	}

	@Test
	void rule18EveryDatabaseLoadingTestActivatesTheIsolatedTestProfile() throws IOException {
		try (var paths = Files.walk(Path.of("src/test/java"))) {
			for (Path source : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
				String text = Files.readString(source);
				if (text.contains("@SpringBootTest") || text.contains("@DataJpaTest") || text.contains("@JdbcTest")) {
					assertThat(text).as(source.toString()).contains("@ActiveProfiles(\"test\")");
				}
			}
		}
	}

	private static List<PathMetadata> snapshot(Path root) throws IOException {
		if (Files.notExists(root)) {
			return List.of();
		}
		try (var paths = Files.walk(root)) {
			return paths.sorted().map(TestDataIsolationTests::metadata).toList();
		}
	}

	private static PathMetadata metadata(Path path) {
		try {
			BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
			return new PathMetadata(path.toString(), attributes.isDirectory(), attributes.size(),
					attributes.lastModifiedTime().toMillis());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read runtime data metadata for " + path, ex);
		}
	}

	private record PathMetadata(String path, boolean directory, long size, long modifiedAt) {
	}

}
