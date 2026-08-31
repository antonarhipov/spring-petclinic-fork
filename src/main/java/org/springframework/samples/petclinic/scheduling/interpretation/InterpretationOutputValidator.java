package org.springframework.samples.petclinic.scheduling.interpretation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.samples.petclinic.availability.ClinicPolicy;
import org.springframework.samples.petclinic.scheduling.request.WindowShape;
import org.springframework.stereotype.Component;

@Component
public class InterpretationOutputValidator {

	public ValidationResult validate(InterpretationCandidate candidate, ClinicPolicy clinicPolicy) {
		if (candidate == null) {
			return new ValidationResult(false, List.of("Candidate cannot be null"));
		}

		List<String> errors = new ArrayList<>();

		if (!"1".equals(candidate.schemaVersion())) {
			errors.add("Invalid schemaVersion: " + candidate.schemaVersion());
		}

		if (candidate.visitReason() == null || candidate.visitReason().isBlank()) {
			errors.add("visitReason must not be blank");
		}
		else if (candidate.visitReason().length() > 1000) {
			errors.add("visitReason must not exceed 1000 characters");
		}

		if (candidate.urgency() == null) {
			errors.add("urgency must not be null");
		}

		if (candidate.durationMinutes() == null) {
			errors.add("durationMinutes must not be null");
		}
		else {
			int dur = candidate.durationMinutes();
			if (dur < 15 || dur > 480 || dur % 15 != 0) {
				errors.add("durationMinutes must be between 15 and 480 and a multiple of 15: " + dur);
			}
			else if (clinicPolicy != null && clinicPolicy.getAllowedDurations() != null
					&& !clinicPolicy.getAllowedDurations().isEmpty()
					&& !clinicPolicy.getAllowedDurations().contains(dur)) {
				errors.add("durationMinutes " + dur + " is not allowed by clinic policy");
			}
		}

		if (candidate.preferredVeterinarian() != null) {
			if (candidate.preferredVeterinarian().id() == null || candidate.preferredVeterinarian().id() <= 0) {
				errors.add("preferredVeterinarian id must be positive");
			}
			if (candidate.preferredVeterinarian().name() == null
					|| candidate.preferredVeterinarian().name().isBlank()) {
				errors.add("preferredVeterinarian name must not be blank");
			}
		}

		if (candidate.requiredSpecialty() != null) {
			if (candidate.requiredSpecialty().id() == null || candidate.requiredSpecialty().id() <= 0) {
				errors.add("requiredSpecialty id must be positive");
			}
			if (candidate.requiredSpecialty().name() == null || candidate.requiredSpecialty().name().isBlank()) {
				errors.add("requiredSpecialty name must not be blank");
			}
		}

		if (candidate.availability() != null) {
			if (candidate.availability().size() > 100) {
				errors.add("availability windows exceed maximum of 100");
			}
			for (int i = 0; i < candidate.availability().size(); i++) {
				WindowCandidate w = candidate.availability().get(i);
				if (w.classification() == null) {
					errors.add("Window[" + i + "]: classification must not be null");
				}
				if (w.shape() == null) {
					errors.add("Window[" + i + "]: shape must not be null");
				}
				if (w.startTime() == null || w.endTime() == null) {
					errors.add("Window[" + i + "]: startTime and endTime must not be null");
				}
				else if (!w.startTime().isBefore(w.endTime())) {
					errors.add("Window[" + i + "]: startTime must be before endTime");
				}
				if (w.sourceText() == null || w.sourceText().isBlank()) {
					errors.add("Window[" + i + "]: sourceText must not be blank");
				}

				if (w.shape() == WindowShape.ONE_OFF) {
					if (w.date() == null) {
						errors.add("Window[" + i + "]: ONE_OFF window requires date");
					}
				}
				else if (w.shape() == WindowShape.WEEKLY) {
					if (w.rangeStart() == null || w.rangeEnd() == null) {
						errors.add("Window[" + i + "]: WEEKLY window requires rangeStart and rangeEnd");
					}
					else if (w.rangeStart().isAfter(w.rangeEnd())) {
						errors.add("Window[" + i + "]: rangeStart must be <= rangeEnd");
					}
					if (w.weekdays() == null || w.weekdays().isEmpty()) {
						errors.add("Window[" + i + "]: WEEKLY window requires at least one weekday");
					}
				}
			}
		}

		return new ValidationResult(errors.isEmpty(), Collections.unmodifiableList(errors));
	}

	public record ValidationResult(boolean valid, List<String> errors) {
	}

}
