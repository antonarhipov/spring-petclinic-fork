package org.springframework.samples.petclinic.scheduling.job;

public enum JobState {

	PENDING, RUNNING, SUCCEEDED, FAILED, STALE;

	public boolean isTerminal() {
		return this == SUCCEEDED || this == FAILED || this == STALE;
	}

}
