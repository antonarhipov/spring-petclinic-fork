package org.springframework.samples.petclinic.scheduling.dto;

import java.time.Instant;
import java.util.Date;

public class DiagnosticsDto {

	private String ollamaStatus;

	private String ollamaModel;

	private String ollamaBaseUrl;

	private String solverStatus;

	private String solverEngine;

	private String lifecycleWorkerStatus;

	private Instant lifecycleLastRun;

	private long lifecycleLastDurationMs;

	private String lifecycleLastError;

	private long lifecycleTotalRuns;

	private String flywayStatus;

	private String flywayVersion;

	private String flywayDescription;

	private Date flywayInstalledOn;

	private int flywayMigrationCount;

	public DiagnosticsDto() {
	}

	public String getOllamaStatus() {
		return ollamaStatus;
	}

	public void setOllamaStatus(String ollamaStatus) {
		this.ollamaStatus = ollamaStatus;
	}

	public String getOllamaModel() {
		return ollamaModel;
	}

	public void setOllamaModel(String ollamaModel) {
		this.ollamaModel = ollamaModel;
	}

	public String getOllamaBaseUrl() {
		return ollamaBaseUrl;
	}

	public void setOllamaBaseUrl(String ollamaBaseUrl) {
		this.ollamaBaseUrl = ollamaBaseUrl;
	}

	public String getSolverStatus() {
		return solverStatus;
	}

	public void setSolverStatus(String solverStatus) {
		this.solverStatus = solverStatus;
	}

	public String getSolverEngine() {
		return solverEngine;
	}

	public void setSolverEngine(String solverEngine) {
		this.solverEngine = solverEngine;
	}

	public String getLifecycleWorkerStatus() {
		return lifecycleWorkerStatus;
	}

	public void setLifecycleWorkerStatus(String lifecycleWorkerStatus) {
		this.lifecycleWorkerStatus = lifecycleWorkerStatus;
	}

	public Instant getLifecycleLastRun() {
		return lifecycleLastRun;
	}

	public void setLifecycleLastRun(Instant lifecycleLastRun) {
		this.lifecycleLastRun = lifecycleLastRun;
	}

	public long getLifecycleLastDurationMs() {
		return lifecycleLastDurationMs;
	}

	public void setLifecycleLastDurationMs(long lifecycleLastDurationMs) {
		this.lifecycleLastDurationMs = lifecycleLastDurationMs;
	}

	public String getLifecycleLastError() {
		return lifecycleLastError;
	}

	public void setLifecycleLastError(String lifecycleLastError) {
		this.lifecycleLastError = lifecycleLastError;
	}

	public long getLifecycleTotalRuns() {
		return lifecycleTotalRuns;
	}

	public void setLifecycleTotalRuns(long lifecycleTotalRuns) {
		this.lifecycleTotalRuns = lifecycleTotalRuns;
	}

	public String getFlywayStatus() {
		return flywayStatus;
	}

	public void setFlywayStatus(String flywayStatus) {
		this.flywayStatus = flywayStatus;
	}

	public String getFlywayVersion() {
		return flywayVersion;
	}

	public void setFlywayVersion(String flywayVersion) {
		this.flywayVersion = flywayVersion;
	}

	public String getFlywayDescription() {
		return flywayDescription;
	}

	public void setFlywayDescription(String flywayDescription) {
		this.flywayDescription = flywayDescription;
	}

	public Date getFlywayInstalledOn() {
		return flywayInstalledOn;
	}

	public void setFlywayInstalledOn(Date flywayInstalledOn) {
		this.flywayInstalledOn = flywayInstalledOn;
	}

	public int getFlywayMigrationCount() {
		return flywayMigrationCount;
	}

	public void setFlywayMigrationCount(int flywayMigrationCount) {
		this.flywayMigrationCount = flywayMigrationCount;
	}

}
