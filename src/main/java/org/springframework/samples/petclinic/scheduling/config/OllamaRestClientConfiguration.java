package org.springframework.samples.petclinic.scheduling.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
public class OllamaRestClientConfiguration {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

	@Bean
	RestClientCustomizer ollamaRestClientTimeoutCustomizer() {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);
		return builder -> builder.requestFactory(requestFactory);
	}

}
