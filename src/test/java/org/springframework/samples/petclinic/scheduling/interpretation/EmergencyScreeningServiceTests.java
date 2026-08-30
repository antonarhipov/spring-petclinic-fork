package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmergencyScreeningServiceTests {

	@Test
	void matchesConfiguredTermWithoutFalsePositive() {
		EmergencyTermRepository terms = mock(EmergencyTermRepository.class);
		AvailabilityRepository policies = mock(AvailabilityRepository.class);
		ClinicSchedulingPolicy policy = new ClinicSchedulingPolicy();
		when(policies.currentPolicy()).thenReturn(policy);
		EmergencyTerm term = new EmergencyTerm();
		term.setTerm("bleeding");
		term.setRuleSetVersion("emergency-v1");
		when(terms.findByPolicyId(policy.getId())).thenReturn(List.of(term));
		EmergencyScreeningService service = new EmergencyScreeningService(terms, policies);
		assertThat(service.screen("The dog is bleeding from a cut").matched()).isTrue();
		assertThat(service.screen("Annual wellness visit for bleeding-heart plant allergy").matched()).isFalse();
	}

	@Test
	void raiseOnlyNeverLowersExistingFlag() {
		EmergencyScreeningService service = new EmergencyScreeningService(mock(EmergencyTermRepository.class),
				mock(AvailabilityRepository.class));
		assertThat(service.raiseOnly(true, false, false)).isTrue();
		assertThat(service.raiseOnly(false, false, false)).isFalse();
	}

}
