package org.springframework.samples.petclinic.config;

import java.time.Duration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Uses the JDK HTTP client for blocking local Ollama calls so the configured read timeout
 * is honored without routing this path through Reactor Netty.
 */
@Configuration(proxyBeanMethods = false)
public class OllamaConfiguration {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	@Bean
	@ConditionalOnMissingBean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.findAndRegisterModules();
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		return mapper;
	}

	@Bean
	@ConfigurationProperties(prefix = "petclinic.ollama")
	public OllamaProperties ollamaProperties() {
		return new OllamaProperties();
	}

	@Bean
	public RestClientCustomizer ollamaRestClientTimeoutCustomizer(OllamaProperties properties) {
		ClientHttpRequestFactory requestFactory = ollamaClientHttpRequestFactory(properties);
		return builder -> builder.requestFactory(requestFactory);
	}

	ClientHttpRequestFactory ollamaClientHttpRequestFactory(OllamaProperties properties) {
		Duration readTimeout = Duration.ofSeconds(properties.getTimeoutSeconds());
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, readTimeout);
		return ClientHttpRequestFactoryBuilder.jdk().build(settings);
	}

	@Bean
	public RestClient ollamaRestClient(RestClient.Builder builder, OllamaProperties properties) {
		return builder.baseUrl(properties.getBaseUrl()).build();
	}

	public static class OllamaProperties {

		private String baseUrl = "http://localhost:11434";

		private String model = "llama3.2";

		private int timeoutSeconds = 10;

		public String getBaseUrl() {
			return this.baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public String getModel() {
			return this.model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		public int getTimeoutSeconds() {
			return this.timeoutSeconds;
		}

		public void setTimeoutSeconds(int timeoutSeconds) {
			this.timeoutSeconds = timeoutSeconds;
		}

	}

}
