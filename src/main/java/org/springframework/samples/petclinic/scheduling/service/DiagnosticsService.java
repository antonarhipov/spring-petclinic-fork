package org.springframework.samples.petclinic.scheduling.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.samples.petclinic.scheduling.dto.DiagnosticsDto;
import org.springframework.stereotype.Service;

@Service
public class DiagnosticsService {

	private static final Logger log = LoggerFactory.getLogger(DiagnosticsService.class);

	private final Optional<Flyway> flyway;

	private final Optional<LifecycleScheduler> lifecycleScheduler;

	private final Optional<ChatModel> chatModel;

	@Value("${spring.ai.ollama.base-url:http://localhost:11434}")
	private String ollamaBaseUrl;

	@Value("${spring.ai.ollama.chat.options.model:llama3.2}")
	private String ollamaModel;

	public DiagnosticsService(@Autowired(required = false) Flyway flyway,
			@Autowired(required = false) LifecycleScheduler lifecycleScheduler,
			@Autowired(required = false) ChatModel chatModel) {
		this.flyway = Optional.ofNullable(flyway);
		this.lifecycleScheduler = Optional.ofNullable(lifecycleScheduler);
		this.chatModel = Optional.ofNullable(chatModel);
	}

	public DiagnosticsDto getDiagnostics() {
		DiagnosticsDto dto = new DiagnosticsDto();

		// 1. Ollama Diagnostics
		dto.setOllamaBaseUrl(ollamaBaseUrl);
		dto.setOllamaModel(ollamaModel);
		if (chatModel.isEmpty()) {
			dto.setOllamaStatus("NOT_CONFIGURED");
		}
		else {
			dto.setOllamaStatus(checkOllamaConnectivity());
		}

		// 2. Solver Diagnostics
		dto.setSolverStatus("READY");
		dto.setSolverEngine("Timefold Solver 2.5.0");

		// 3. Lifecycle Worker Diagnostics
		if (lifecycleScheduler.isPresent()) {
			LifecycleScheduler scheduler = lifecycleScheduler.get();
			dto.setLifecycleWorkerStatus(scheduler.isHealthy() ? "HEALTHY" : "DEGRADED");
			dto.setLifecycleLastRun(scheduler.getLastRunTime());
			dto.setLifecycleLastDurationMs(scheduler.getLastRunDurationMs());
			dto.setLifecycleLastError(scheduler.getLastError());
			dto.setLifecycleTotalRuns(scheduler.getTotalRunsCount());
		}
		else {
			dto.setLifecycleWorkerStatus("NOT_CONFIGURED");
		}

		// 4. Flyway Migration Diagnostics
		if (flyway.isPresent()) {
			try {
				MigrationInfoService infoService = flyway.get().info();
				MigrationInfo current = infoService.current();
				if (current != null) {
					dto.setFlywayStatus(current.getState().name());
					dto.setFlywayVersion(current.getVersion() != null ? current.getVersion().getVersion() : "Unknown");
					dto.setFlywayDescription(current.getDescription());
					dto.setFlywayInstalledOn(current.getInstalledOn());
				}
				else {
					dto.setFlywayStatus("INITIALIZED");
					dto.setFlywayVersion("None");
				}
				dto.setFlywayMigrationCount(infoService.applied() != null ? infoService.applied().length : 0);
			}
			catch (Exception e) {
				log.warn("Failed to retrieve Flyway migration info: {}", e.getMessage());
				dto.setFlywayStatus("ERROR");
			}
		}
		else {
			dto.setFlywayStatus("DISABLED");
		}

		return dto;
	}

	private String checkOllamaConnectivity() {
		try {
			HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(1000)).build();

			String targetUrl = ollamaBaseUrl.replaceAll("/+$", "") + "/api/tags";
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(targetUrl))
				.timeout(Duration.ofMillis(1500))
				.GET()
				.build();

			HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() == 200) {
				return "UP";
			}
			else {
				return "DOWN (HTTP " + response.statusCode() + ")";
			}
		}
		catch (Exception e) {
			return "DOWN";
		}
	}

}
