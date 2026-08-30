package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.Map;
import java.util.Set;

public record ClinicVocabulary(Set<Integer> allowedDurations, Set<String> specialtyCodes, Set<String> veterinarianCodes,
		Map<String, Integer> specialtyIdsByCode, Map<String, Integer> veterinarianIdsByCode, String clinicZoneId) {
}
