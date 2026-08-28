package org.springframework.samples.petclinic.scheduling.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ollama.autoconfigure.OllamaChatProperties;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class OllamaChatConfigurationTests {

	@Test
	void boundsStructuredExtractionWithoutModelThinking() throws IOException {
		Properties applicationProperties = PropertiesLoaderUtils.loadAllProperties("application.properties");
		Map<String, Object> propertyValues = applicationProperties.entrySet()
			.stream()
			.collect(Collectors.toMap(entry -> entry.getKey().toString(), Map.Entry::getValue));
		OllamaChatProperties properties = new Binder(new MapConfigurationPropertySource(propertyValues))
			.bind("spring.ai.ollama.chat", Bindable.of(OllamaChatProperties.class))
			.get();

		OllamaChatOptions options = properties.toOptions();
		assertThat(options.getThinkOption()).isEqualTo(ThinkOption.ThinkBoolean.DISABLED);
		assertThat(options.getNumPredict()).isEqualTo(128);
		assertThat(options.getKeepAlive()).isEqualTo("10m");

	}

	@Test
	void configuresJdkClientWithLongOllamaReadTimeout() {
		RestClient.Builder builder = RestClient.builder();
		new OllamaChatClientConfiguration().ollamaRestClientTimeoutCustomizer().customize(builder);

		Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(Duration.ofSeconds(120));
		HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
		assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(10));
	}

}
