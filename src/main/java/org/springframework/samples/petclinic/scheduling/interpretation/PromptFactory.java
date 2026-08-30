package org.springframework.samples.petclinic.scheduling.interpretation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

	private final String template;

	public PromptFactory() {
		try {
			this.template = new ClassPathResource("prompts/appointment-interpretation-v1.st")
				.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	public String render(String sourceText, Instant submittedAt, Instant now, ClinicVocabulary vocabulary,
			int bookingHorizonDays, int minimumNoticeMinutes) {
		return this.template.replace("{clinicZone}", vocabulary.clinicZoneId())
			.replace("{now}", now.toString())
			.replace("{allowedDurations}", vocabulary.allowedDurations().toString())
			.replace("{bookingHorizonDays}", Integer.toString(bookingHorizonDays))
			.replace("{ownerMinimumNoticeMinutes}", Integer.toString(minimumNoticeMinutes))
			.replace("{namedPeriods}", "")
			.replace("{veterinarians}", vocabulary.veterinarianCodes().toString())
			.replace("{specialties}", vocabulary.specialtyCodes().toString())
			.replace("{submittedAt}", submittedAt.toString())
			.replace("{sourceText}", sourceText);
	}

}
