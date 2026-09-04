/*
 * Copyright 2012-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package org.springframework.samples.petclinic.scheduling.config;

import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatConfigurationTests {

	@Test
	void configuresTheProvenJdkConnectAndReadTimeouts() {
		RestClient.Builder builder = RestClient.builder();
		new OllamaChatConfiguration().ollamaRestClientTimeoutCustomizer(Duration.ofSeconds(60)).customize(builder);

		Object requestFactory = ReflectionTestUtils.getField(builder, "requestFactory");
		assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
		assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout")).isEqualTo(Duration.ofSeconds(60));
		HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
		assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(10));
	}

	@Test
	void usesTheSpringAiTwoModelPropertyWithoutDeprecatedOptionsOrCustomAlias() throws Exception {
		String properties = Files.readString(Path.of("src/main/resources/application.properties"));
		assertThat(properties).contains("spring.ai.ollama.chat.model=${SPRING_AI_OLLAMA_CHAT_MODEL:");
		assertThat(properties).doesNotContain("spring.ai.ollama.chat.options.");
		assertThat(properties).doesNotContain("scheduling.ai.model");
	}

}
