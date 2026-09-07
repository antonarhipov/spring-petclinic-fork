# Proven Spring Boot 4 Ollama Timeouts

Use this configuration to give Spring AI's Ollama HTTP client explicit connect and read timeouts. The pattern has been verified with Spring Boot 4.1.0 and Spring AI 2.0.1. Re-check class names and customization behavior when changing major or minor framework versions.

## Configuration

```java
import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
class OllamaChatClientConfiguration {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	@Bean
	ChatClient ollamaChatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RestClientCustomizer ollamaRestClientTimeoutCustomizer() {
		HttpClientSettings settings = HttpClientSettings.defaults()
			.withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);
		return builder -> builder.requestFactory(requestFactory);
	}

}
```

The important sequence is:

1. Start with `HttpClientSettings.defaults()`.
2. Apply both timeouts with `withTimeouts(connectTimeout, readTimeout)`.
3. Build a JDK-backed `ClientHttpRequestFactory` through `ClientHttpRequestFactoryBuilder.jdk()`.
4. Install that factory through a `RestClientCustomizer` bean so the auto-configured Ollama client receives it.

The 10-second connect and 120-second read values are practical defaults for local inference: failure to establish a connection should surface quickly, while generation may legitimately take longer. Tune them for the selected model, hardware, input size, and service-level objective.

This bounds connection establishment and response reading. It is not an end-to-end deadline and does not add cancellation or retry behavior.

## Verification Test

Verify the actual request-factory fields rather than merely asserting that the customizer bean exists:

```java
import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class OllamaChatClientConfigurationTests {

	@Test
	void configuresJdkClientWithOllamaTimeouts() {
		RestClient.Builder builder = RestClient.builder();
		new OllamaChatClientConfiguration().ollamaRestClientTimeoutCustomizer().customize(builder);

		Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
			.isEqualTo(Duration.ofSeconds(120));

		HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
		assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(10));
	}

}
```

This is a focused unit test of the proven configuration. Add an application-context test if the target application changes auto-configuration, defines multiple `ChatClient.Builder` beans, or conditionally registers the customizer.

## Scope Warning

`RestClientCustomizer` is a general Spring Boot customization hook. It can replace the request factory for other Boot-managed `RestClient.Builder` instances. Inspect other HTTP clients before adopting these timeout values globally. If different clients require different policies, use a narrower, version-supported builder or model configuration and retain an equivalent request-factory test.

Exposing a `RestClient.Builder` bean with the request factory pre-applied is an accepted variant of the same pattern, but it is broader, not narrower: it replaces Boot's auto-configured `RestClient.Builder` for every consumer in the context. Prefer the `RestClientCustomizer` shown above unless the application has a specific reason to own the builder, and keep the same request-factory assertions in either case.
