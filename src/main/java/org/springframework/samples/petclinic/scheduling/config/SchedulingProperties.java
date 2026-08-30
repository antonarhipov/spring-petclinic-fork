package org.springframework.samples.petclinic.scheduling.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "petclinic.scheduling")
public class SchedulingProperties {

	private Duration llmDeadline = Duration.ofSeconds(10);

	private Duration matchDeadline = Duration.ofSeconds(5);

	private final Executor executor = new Executor();

	public Duration getLlmDeadline() {
		return this.llmDeadline;
	}

	public void setLlmDeadline(Duration llmDeadline) {
		this.llmDeadline = llmDeadline;
	}

	public Duration getMatchDeadline() {
		return this.matchDeadline;
	}

	public void setMatchDeadline(Duration matchDeadline) {
		this.matchDeadline = matchDeadline;
	}

	public Executor getExecutor() {
		return this.executor;
	}

	public static class Executor {

		private int corePoolSize = 2;

		private int maxPoolSize = 4;

		private int queueCapacity = 50;

		public int getCorePoolSize() {
			return this.corePoolSize;
		}

		public void setCorePoolSize(int corePoolSize) {
			this.corePoolSize = corePoolSize;
		}

		public int getMaxPoolSize() {
			return this.maxPoolSize;
		}

		public void setMaxPoolSize(int maxPoolSize) {
			this.maxPoolSize = maxPoolSize;
		}

		public int getQueueCapacity() {
			return this.queueCapacity;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

	}

}
