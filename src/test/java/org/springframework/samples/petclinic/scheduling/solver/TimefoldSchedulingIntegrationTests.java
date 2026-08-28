package org.springframework.samples.petclinic.scheduling.solver;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import org.junit.jupiter.api.Test;

class TimefoldSchedulingIntegrationTests {

	@Test
	void planningTypesAreDiscoveredByTimefold() {
		assertThat(contains(AppointmentPlanningSolution.class.getAnnotations(), "PlanningSolution")).isTrue();
		assertThat(contains(AppointmentPlanningEntity.class.getAnnotations(), "PlanningEntity")).isTrue();
	}

	private boolean contains(Annotation[] annotations, String name) {
		for (Annotation annotation : annotations) {
			if (annotation.annotationType().getSimpleName().equals(name)) {
				return true;
			}
		}
		return false;
	}

}
