package org.springframework.samples.petclinic.scheduling.interpretation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.samples.petclinic.scheduling.availability.AvailabilityRepository;
import org.springframework.stereotype.Service;

@Service
public class EmergencyScreeningService {

	private final EmergencyTermRepository terms;

	private final AvailabilityRepository policies;

	public EmergencyScreeningService(EmergencyTermRepository terms, AvailabilityRepository policies) {
		this.terms = terms;
		this.policies = policies;
	}

	public ScreenResult screen(String sourceText) {
		String normalized = normalize(sourceText);
		List<EmergencyTerm> configured = this.terms.findByPolicyId(this.policies.currentPolicy().getId());
		if (configured.isEmpty()) {
			configured = defaultTerms();
		}
		List<String> matches = new ArrayList<>();
		String version = "emergency-v1";
		for (EmergencyTerm term : configured) {
			version = term.getRuleSetVersion();
			String needle = normalize(term.getTerm());
			if (!needle.isBlank() && containsWord(normalized, needle)) {
				matches.add(term.getTerm());
			}
		}
		return new ScreenResult(version, matches, !matches.isEmpty());
	}

	public boolean raiseOnly(boolean alreadyUrgent, boolean screenMatched, boolean llmUrgent) {
		return alreadyUrgent || screenMatched || llmUrgent;
	}

	static String normalize(String text) {
		if (text == null) {
			return "";
		}
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-\\s]", " ").replaceAll("\\s+", " ").trim();
	}

	private boolean containsWord(String haystack, String needle) {
		return (" " + haystack + " ").contains(" " + needle + " ");
	}

	private List<EmergencyTerm> defaultTerms() {
		return List.of(term("bleeding"), term("unconscious"), term("seizure"), term("poison"), term("collapse"));
	}

	private EmergencyTerm term(String value) {
		EmergencyTerm term = new EmergencyTerm();
		term.setTerm(value);
		term.setRuleSetVersion("emergency-v1");
		return term;
	}

	public record ScreenResult(String version, List<String> matchedTerms, boolean matched) {
	}

}
