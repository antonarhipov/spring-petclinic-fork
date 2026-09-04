/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.scheduling.interpretation;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the shipped default provider can be wired without making a model call. */
@SpringBootTest(properties = "scheduling.ai.provider=ollama")
class OllamaProviderContextTests {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private RequestInterpreter interpreter;

	@Test
	void defaultOllamaProviderContextStartsWithoutCallingTheModel() {
		assertThat(this.interpreter).isInstanceOf(OllamaRequestInterpreter.class);
		assertThat(this.context.getBeansOfType(RequestInterpreter.class)).hasSize(1);
		assertThat(this.context.getBean("schedulingOllamaChatClient")).isInstanceOf(ChatClient.class);
	}

}
