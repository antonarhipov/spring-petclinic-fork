/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.scheduling.ai;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;

/**
 * HTTP client configuration for the (local) Ollama chat calls made by
 * {@link AppointmentInterpreter}.
 *
 * <p>
 * Local LLM inference (a "thinking" model producing structured JSON) can take tens of
 * seconds. Spring AI's {@code OllamaApi} performs its blocking chat call through the
 * Spring Boot auto-configured {@link org.springframework.web.client.RestClient.Builder}.
 * With reactor-netty on the classpath (pulled in transitively for the streaming
 * {@code WebClient}), Boot auto-selects the reactor-netty request factory, which:
 * <ol>
 * <li>does not honour a configured read timeout, aborting long calls at ~10s with
 * {@code io.netty.handler.timeout.ReadTimeoutException} before Ollama responds, and</li>
 * <li>logs an {@code ERROR} trying to load the native macOS DNS resolver
 * ({@code io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider} /
 * {@code UnsatisfiedLinkError}).</li>
 * </ol>
 *
 * <p>
 * Relying on {@code spring.http.clients.*} properties to switch the factory proved
 * unreliable, so this customizer sets the request factory (and its timeouts) explicitly
 * in code. Because Boot applies every {@link RestClientCustomizer} to the shared
 * {@code RestClient.Builder} that {@code OllamaApi} consumes, the blocking Ollama call is
 * guaranteed to use the JDK {@code HttpClient} with a generous read timeout, which also
 * avoids reactor-netty (and therefore the native macOS DNS resolver error) on this path.
 */
@Configuration(proxyBeanMethods = false)
public class AiHttpClientConfig {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);

	@Bean
	public RestClientCustomizer ollamaRestClientTimeoutCustomizer() {
		HttpClientSettings settings = HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT);
		ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.jdk().build(settings);
		return builder -> builder.requestFactory(requestFactory);
	}

}
