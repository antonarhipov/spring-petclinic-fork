package org.springframework.samples.petclinic.scheduling.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OllamaChatClientConfiguration {

	@Bean
	ChatClient schedulingChatClient(ChatClient.Builder builder) {
		return builder.build();
	}

}
