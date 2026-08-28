package org.springframework.samples.petclinic.scheduling.ai;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Component
class OllamaRestClientLoggingCustomizer implements RestClientCustomizer {

	private static final Logger logger = LoggerFactory.getLogger(OllamaRestClientLoggingCustomizer.class);

	private final URI chatUri;

	OllamaRestClientLoggingCustomizer(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
		this.chatUri = URI.create(baseUrl).resolve("/api/chat");
	}

	@Override
	public void customize(RestClient.Builder restClientBuilder) {
		restClientBuilder.bufferContent(this::isOllamaChat).requestInterceptor(this::logExchange);
	}

	private ClientHttpResponse logExchange(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		if (!logger.isDebugEnabled() || !isOllamaChat(request.getURI(), request.getMethod())) {
			return execution.execute(request, body);
		}
		logger.debug("Ollama request method={} uri={} body={}", request.getMethod(), request.getURI(), text(body));
		try {
			ClientHttpResponse response = execution.execute(request, body);
			byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
			logger.debug("Ollama response status={} body={}", response.getStatusCode(), text(responseBody));
			return response;
		}
		catch (IOException | RuntimeException ex) {
			logger.debug("Ollama exchange failed method={} uri={}", request.getMethod(), request.getURI(), ex);
			throw ex;
		}
	}

	private boolean isOllamaChat(URI uri, HttpMethod method) {
		return HttpMethod.POST.equals(method) && this.chatUri.equals(uri);
	}

	private String text(byte[] body) {
		return new String(body, StandardCharsets.UTF_8);
	}

}
