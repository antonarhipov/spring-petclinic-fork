package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InterpretationCandidate(String schemaVersion, String visitReason, Urgency urgency,
		List<String> urgencySignals, Integer durationMinutes, CatalogChoice preferredVeterinarian,
		CatalogChoice requiredSpecialty, List<WindowCandidate> availability, List<String> contradictions,
		List<UnresolvedItem> unresolved) {
}
