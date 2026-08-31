package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

public record InterpretationPrompt(String prose, String petName, String petTypeName, ZoneId clinicZone,
		List<Integer> allowedDurations, List<CatalogChoice> availableVets, List<CatalogChoice> availableSpecialties,
		Instant submittedAt) {
}
