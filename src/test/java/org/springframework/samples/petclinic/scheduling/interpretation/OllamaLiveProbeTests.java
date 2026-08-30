package org.springframework.samples.petclinic.scheduling.interpretation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "PETCLINIC_OLLAMA_LIVE", matches = "true")
class OllamaLiveProbeTests {

	@Test
	@Timeout(15)
	void prewarmedGemma4ReturnsStructuredOutputWithinDeadline() throws Exception {
		String host = System.getenv().getOrDefault("OLLAMA_HOST", "http://127.0.0.1:11434");
		String body = """
				{"model":"gemma4:latest","stream":false,"format":"json","options":{"temperature":0},
				"prompt":"Return JSON with schemaVersion 1.0, visitReason Annual checkup, durationMinutes 30, careType GENERAL, urgency NO_CONCERN_IDENTIFIED, empty window arrays."}
				""";
		Instant deadline = Instant.now().plusSeconds(10);
		HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/api/generate"))
			.timeout(Duration.ofSeconds(10))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build();
		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
		assertThat(Instant.now()).isBefore(deadline);
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("gemma4");
		assertThat(response.body()).contains("response");
	}

}
