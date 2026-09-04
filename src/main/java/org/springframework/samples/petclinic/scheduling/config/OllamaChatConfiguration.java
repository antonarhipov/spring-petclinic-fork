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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "scheduling.ai.provider", havingValue = "ollama")
public class OllamaChatConfiguration {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	@Bean
	ChatClient schedulingOllamaChatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RestClientCustomizer ollamaRestClientTimeoutCustomizer() {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);
		return builder -> builder.requestFactory(requestFactory);
	}

}
