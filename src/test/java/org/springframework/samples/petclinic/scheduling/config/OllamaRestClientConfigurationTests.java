package org.springframework.samples.petclinic.scheduling.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaRestClientConfigurationTests {

	@Test
	void customizerUsesJdkClientWithOllamaTimeouts() {
		OllamaRestClientConfiguration configuration = new OllamaRestClientConfiguration();
		RestClientCustomizer customizer = configuration.ollamaRestClientTimeoutCustomizer();
		RestClient.Builder builder = RestClient.builder();

		customizer.customize(builder);

		Object requestFactory = ReflectionTestUtils.getField(builder.build(), "clientRequestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(Duration.ofMinutes(5));

		HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
		assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(10));
	}

}
