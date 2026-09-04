/*
 * Copyright 2012-2026 the original author or authors.
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

package org.springframework.samples.petclinic.scheduling.interpretation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.samples.petclinic.scheduling.config.OllamaChatConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The automated suite runs against the deterministic stub interpreter and never wires the
 * live model (AC-137), while the shipped default stays {@code ollama} (RULE-17).
 */
@SpringBootTest
class SchedulingProviderPinningTests {

	private static final Path MAIN_PROPERTIES = Path.of("src/main/resources/application.properties");

	private static final Path TEST_PROPERTIES = Path.of("src/test/resources/application.properties");

	@Autowired
	private ApplicationContext context;

	@Autowired
	private Environment environment;

	@Autowired
	private RequestInterpreter interpreter;

	@Test
	@DisplayName("AC-137: the test context uses the stub interpreter and has no Ollama interpreter or ChatClient bean")
	void testContextPinsTheStubInterpreterAndHasNoLiveModelBeans_AC137() {
		assertThat(this.environment.getProperty("scheduling.ai.provider")).isEqualTo("stub");
		assertThat(this.interpreter).isInstanceOf(StubRequestInterpreter.class);
		assertThat(this.context.getBeansOfType(RequestInterpreter.class)).hasSize(1);
		assertThat(this.context.getBeanNamesForType(OllamaRequestInterpreter.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(OllamaChatConfiguration.class)).isEmpty();
		assertThat(this.context.getBeanNamesForType(ChatClient.class)).isEmpty();
	}

	@Test
	@DisplayName("RULE-17: the shipped default provider is ollama; only the test copy pins stub")
	void shippedDefaultIsOllamaAndTestCopyDiffersOnlyInTheProvider_AC137() throws IOException {
		Properties main = load(MAIN_PROPERTIES);
		Properties test = load(TEST_PROPERTIES);

		assertThat(main.getProperty("scheduling.ai.provider")).isEqualTo("${SCHEDULING_AI_PROVIDER:ollama}");
		assertThat(test.getProperty("scheduling.ai.provider")).isEqualTo("stub");

		main.remove("scheduling.ai.provider");
		test.remove("scheduling.ai.provider");
		assertThat(test).as("src/test/resources/application.properties shadows the main file and must stay in sync")
			.isEqualTo(main);
	}

	private static Properties load(Path path) throws IOException {
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(path)) {
			properties.load(reader);
		}
		return properties;
	}

}
