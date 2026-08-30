package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.samples.petclinic.scheduling.availability.AllowedDurationRepository;
import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.samples.petclinic.scheduling.availability.ClinicSchedulingPolicy;
import org.springframework.samples.petclinic.vet.Specialty;
import org.springframework.samples.petclinic.vet.Vet;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Component;

@Component
public class ClinicVocabularyFactory {

	private final AvailabilityRepository policies;

	private final AllowedDurationRepository durations;

	private final VetRepository vets;

	public ClinicVocabularyFactory(AvailabilityRepository policies, AllowedDurationRepository durations,
			VetRepository vets) {
		this.policies = policies;
		this.durations = durations;
		this.vets = vets;
	}

	public ClinicVocabulary current() {
		ClinicSchedulingPolicy policy = this.policies.currentPolicy();
		Set<Integer> allowed = new LinkedHashSet<>();
		this.durations.findByPolicyId(policy.getId()).forEach(d -> allowed.add(d.getDurationMinutes()));
		Map<String, Integer> specialtyIds = new LinkedHashMap<>();
		Map<String, Integer> vetIds = new LinkedHashMap<>();
		for (Vet vet : this.vets.findAll()) {
			vetIds.put(vet.getLastName().toLowerCase(), vet.getId());
			for (Specialty specialty : vet.getSpecialties()) {
				specialtyIds.put(specialty.getName().toLowerCase(), specialty.getId());
			}
		}
		return new ClinicVocabulary(allowed, specialtyIds.keySet(), vetIds.keySet(), specialtyIds, vetIds,
				policy.getZoneId());
	}

}
