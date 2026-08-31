package org.springframework.samples.petclinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.samples.petclinic.config.OllamaConfiguration.OllamaProperties;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaConfigurationTests {

	@Test
	void usesJdkRequestFactoryForOllamaCalls() {
		OllamaProperties properties = new OllamaProperties();
		properties.setTimeoutSeconds(120);

		assertThat(new OllamaConfiguration().ollamaClientHttpRequestFactory(properties))
			.isInstanceOf(JdkClientHttpRequestFactory.class);
	}

}
