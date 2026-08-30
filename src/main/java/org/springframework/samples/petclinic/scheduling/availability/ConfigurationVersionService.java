package org.springframework.samples.petclinic.scheduling.availability;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class ConfigurationVersionService {

	private final AvailabilityRepository policies;

	private final Clock clock;

	public ConfigurationVersionService(AvailabilityRepository policies, Clock clock) {
		this.policies = policies;
		this.clock = clock;
	}

	public long bump() {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		policy.setConfigurationVersion(policy.getConfigurationVersion() + 1);
		policy.setUpdatedAt(Instant.now(this.clock));
		this.policies.save(policy);
		return policy.getConfigurationVersion();
	}

}
