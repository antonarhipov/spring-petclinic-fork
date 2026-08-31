package org.springframework.samples.petclinic.scheduling.interpretation;

public enum Urgency {

	ROUTINE, PRIORITY, EMERGENCY_SUSPECTED;

	public boolean isRoutine() {
		return this == ROUTINE;
	}

	public boolean isPriority() {
		return this == PRIORITY;
	}

	public boolean isEmergency() {
		return this == EMERGENCY_SUSPECTED;
	}

}
