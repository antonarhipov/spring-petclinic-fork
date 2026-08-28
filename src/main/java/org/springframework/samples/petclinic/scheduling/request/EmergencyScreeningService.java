package org.springframework.samples.petclinic.scheduling.request;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class EmergencyScreeningService {

	private static final Set<String> TERMS = Set.of("unconscious", "not breathing", "seizure", "poison", "bleeding");

	public boolean indicatesEmergency(String sourceText) {
		String normalized = sourceText.toLowerCase(Locale.ROOT);
		return TERMS.stream().anyMatch(normalized::contains);
	}

}
