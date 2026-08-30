package org.springframework.samples.petclinic.scheduling.integration;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.samples.petclinic.scheduling.interpretation.ClinicVocabulary;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationAuditMapper;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationClassification;
import org.springframework.samples.petclinic.scheduling.interpretation.InterpretationSchemaValidator;

import static org.assertj.core.api.Assertions.assertThat;

class InterpretationAuditTests {

	@Test
	void unknownFieldsAreSerializedForAuditOnly() {
		String raw = """
				{"schemaVersion":"1.0","visitReason":"Annual checkup","durationMinutes":30,
				"careType":"GENERAL","requiredSpecialtyCode":null,"allowedWindows":[],"preferredWindows":[],
				"excludedWindows":[],"preferredVeterinarianCode":null,"veterinarianPreferenceStrength":"NONE",
				"urgency":"NO_CONCERN_IDENTIFIED","unresolvedDates":[],"uncertainties":[],"confidence":0.4}
				""";
		ClinicVocabulary vocabulary = new ClinicVocabulary(Set.of(30), Set.of(), Set.of(), Map.of(), Map.of(),
				"America/Chicago");
		var validation = new InterpretationSchemaValidator().validate(raw, vocabulary);

		assertThat(validation.classification()).isEqualTo(InterpretationClassification.VALID_REVIEWABLE);
		assertThat(validation.unknownFields()).containsEntry("/confidence", "0.4");
		assertThat(validation.recognizedJson()).doesNotContain("confidence");
		assertThat(new InterpretationAuditMapper().unknownFieldsJson(validation.unknownFields()))
			.contains("confidence");
	}

}
