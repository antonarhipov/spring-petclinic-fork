package org.springframework.samples.petclinic.scheduling.request;

@FunctionalInterface
public interface InterpretationLauncher {

	void launch(int requestId);

}
