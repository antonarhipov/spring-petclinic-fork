/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.config;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaChatConfiguration {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	@Bean
	ChatClient schedulingOllamaChatClient(ChatClient.Builder builder) {
		// The advisor observes the raw ChatResponse before typed conversion, so malformed
		// structured output remains diagnosable.
		return builder.defaultAdvisors(new SimpleLoggerAdvisor()).build();
	}

	@Bean
	public RestClientCustomizer ollamaRestClientTimeoutCustomizer(
			@Value("${scheduling.ai.timeout:60s}") Duration readTimeout) {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, readTimeout);
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);
		return builder -> builder.requestFactory(requestFactory);
	}

}
