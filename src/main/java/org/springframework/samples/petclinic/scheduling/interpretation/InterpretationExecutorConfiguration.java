package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Duration;
import java.util.concurrent.Executor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class InterpretationExecutorConfiguration {

	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	@Bean
	ChatClient ollamaChatClient(ChatClient.Builder builder) {
		return builder.build();
	}

	@Bean
	RestClientCustomizer ollamaRestClientTimeoutCustomizer() {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		return builder -> builder.requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings));
	}

	@Bean(name = "interpretationExecutor")
	Executor interpretationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(Integer.MAX_VALUE);
		executor.setThreadNamePrefix("interpretation-");
		executor.setDaemon(true);
		executor.initialize();
		return executor;
	}

}
