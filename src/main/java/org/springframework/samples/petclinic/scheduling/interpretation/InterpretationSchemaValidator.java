package org.springframework.samples.petclinic.scheduling.interpretation;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

@Component
public class InterpretationSchemaValidator {

	private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "visitReason", "durationMinutes", "careType",
			"requiredSpecialtyCode", "allowedWindows", "preferredWindows", "excludedWindows",
			"preferredVeterinarianCode", "veterinarianPreferenceStrength", "urgency", "unresolvedDates",
			"uncertainties");

	private static final Set<String> WINDOW_FIELDS = Set.of("sourcePhrase", "resolvedStart", "resolvedEnd",
			"resolution", "fallbackAllowed");

	private static final Set<String> UNRESOLVED_FIELDS = Set.of("sourcePhrase", "reason");

	private static final Set<String> UNCERTAINTY_FIELDS = Set.of("fieldPath", "code");

	private final ObjectMapper mapper = new ObjectMapper();

	private final JsonSchema schema;

	private final Validator jakartaValidator = Validation.buildDefaultValidatorFactory().getValidator();

	public InterpretationSchemaValidator() {
		try (InputStream in = new ClassPathResource("schemas/llm-interpretation-v1.schema.json").getInputStream()) {
			JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
			this.schema = factory.getSchema(this.mapper.readTree(in));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable to load interpretation schema", ex);
		}
	}

	public InterpretationValidationResult validate(String rawJson, ClinicVocabulary vocabulary) {
		JsonNode tree;
		try {
			tree = this.mapper.readTree(rawJson);
		}
		catch (Exception ex) {
			return invalid("MALFORMED_JSON");
		}
		if (!tree.isObject()) {
			return invalid("MALFORMED_JSON");
		}
		Map<String, Object> unknown = new LinkedHashMap<>();
		stripUnknown((ObjectNode) tree, "", unknown);
		Set<ValidationMessage> schemaErrors = this.schema.validate(tree);
		boolean schemaInvalid = !schemaErrors.isEmpty();
		AppointmentInterpretationV1 dto;
		try {
			dto = this.mapper.treeToValue(tree, AppointmentInterpretationV1.class);
		}
		catch (Exception ex) {
			return new InterpretationValidationResult(InterpretationClassification.INVALID_STRUCTURED_OUTPUT, null,
					unknown, List.of("BIND_FAILED"), null);
		}
		Set<ConstraintViolation<AppointmentInterpretationV1>> violations = this.jakartaValidator.validate(dto);
		if (!violations.isEmpty() || (schemaInvalid && dto.uncertainties() == null)) {
			return new InterpretationValidationResult(InterpretationClassification.INVALID_STRUCTURED_OUTPUT, dto,
					unknown, List.of("JAKARTA_INVALID"), null);
		}
		List<String> semantic = semanticIssues(dto, vocabulary);
		if (semantic.stream().anyMatch(code -> code.startsWith("INVALID_"))) {
			return new InterpretationValidationResult(InterpretationClassification.INVALID_STRUCTURED_OUTPUT, dto,
					unknown, semantic, writeJson(dto));
		}
		boolean needsStaff = !dto.uncertainties().isEmpty() || !dto.unresolvedDates().isEmpty()
				|| "UNRESOLVED".equals(dto.careType()) || "UNRESOLVED".equals(dto.urgency())
				|| "UNRESOLVED".equals(dto.veterinarianPreferenceStrength()) || semantic.contains("UNRESOLVED_DATE")
				|| dto.allowedWindows().stream().anyMatch(w -> "UNRESOLVED".equals(w.resolution()));
		InterpretationClassification classification = needsStaff ? InterpretationClassification.VALID_NEEDS_STAFF
				: InterpretationClassification.VALID_REVIEWABLE;
		return new InterpretationValidationResult(classification, dto, unknown, semantic, writeJson(dto));
	}

	private List<String> semanticIssues(AppointmentInterpretationV1 dto, ClinicVocabulary vocabulary) {
		List<String> issues = new ArrayList<>();
		if (!vocabulary.allowedDurations().contains(dto.durationMinutes())) {
			issues.add("INVALID_DURATION");
		}
		if ("SPECIALTY".equals(dto.careType())) {
			if (dto.requiredSpecialtyCode() == null
					|| !vocabulary.specialtyCodes().contains(dto.requiredSpecialtyCode())) {
				issues.add("INVALID_SPECIALTY");
			}
		}
		else if (dto.requiredSpecialtyCode() != null) {
			issues.add("INVALID_SPECIALTY_PRESENT");
		}
		if ("NONE".equals(dto.veterinarianPreferenceStrength())) {
			if (dto.preferredVeterinarianCode() != null) {
				issues.add("INVALID_VET_PREFERENCE");
			}
		}
		else if (!"UNRESOLVED".equals(dto.veterinarianPreferenceStrength())) {
			if (dto.preferredVeterinarianCode() == null
					|| !vocabulary.veterinarianCodes().contains(dto.preferredVeterinarianCode())) {
				issues.add("INVALID_VET_CODE");
			}
		}
		validateWindows(dto.allowedWindows(), issues);
		validateWindows(dto.preferredWindows(), issues);
		validateWindows(dto.excludedWindows(), issues);
		for (AppointmentInterpretationV1.UncertaintyV1 uncertainty : dto.uncertainties()) {
			if (!uncertainty.fieldPath().startsWith("/")) {
				issues.add("INVALID_UNCERTAINTY_PATH");
			}
		}
		return issues;
	}

	private void validateWindows(List<AppointmentInterpretationV1.WindowV1> windows, List<String> issues) {
		for (AppointmentInterpretationV1.WindowV1 window : windows) {
			if ("RESOLVED".equals(window.resolution())) {
				if (window.resolvedStart() == null || window.resolvedEnd() == null) {
					issues.add("INVALID_WINDOW");
					continue;
				}
				try {
					OffsetDateTime start = OffsetDateTime.parse(window.resolvedStart());
					OffsetDateTime end = OffsetDateTime.parse(window.resolvedEnd());
					if (!end.isAfter(start)) {
						issues.add("INVALID_WINDOW_ORDER");
					}
				}
				catch (Exception ex) {
					issues.add("INVALID_WINDOW");
				}
			}
			else {
				issues.add("UNRESOLVED_DATE");
			}
		}
	}

	private void stripUnknown(ObjectNode node, String path, Map<String, Object> unknown) {
		Iterator<String> names = node.fieldNames();
		List<String> toRemove = new ArrayList<>();
		while (names.hasNext()) {
			String name = names.next();
			String pointer = path + "/" + name;
			boolean known = knownField(path, name);
			JsonNode child = node.get(name);
			if (!known) {
				unknown.put(pointer, child.isValueNode() ? child.asText() : child.toString());
				toRemove.add(name);
			}
			else if (child.isObject()) {
				stripUnknown((ObjectNode) child, pointer, unknown);
			}
			else if (child.isArray()) {
				for (int i = 0; i < child.size(); i++) {
					if (child.get(i).isObject()) {
						stripUnknown((ObjectNode) child.get(i), pointer + "/" + i, unknown);
					}
				}
			}
		}
		toRemove.forEach(node::remove);
	}

	private boolean knownField(String path, String name) {
		if (path.isEmpty()) {
			return ROOT_FIELDS.contains(name);
		}
		if (path.contains("allowedWindows") || path.contains("preferredWindows") || path.contains("excludedWindows")) {
			return WINDOW_FIELDS.contains(name);
		}
		if (path.contains("unresolvedDates")) {
			return UNRESOLVED_FIELDS.contains(name);
		}
		if (path.contains("uncertainties")) {
			return UNCERTAINTY_FIELDS.contains(name);
		}
		return false;
	}

	private InterpretationValidationResult invalid(String code) {
		return new InterpretationValidationResult(InterpretationClassification.INVALID_STRUCTURED_OUTPUT, null,
				Map.of(), List.of(code), null);
	}

	private String writeJson(AppointmentInterpretationV1 dto) {
		try {
			return this.mapper.writeValueAsString(dto);
		}
		catch (Exception ex) {
			return "{}";
		}
	}

}
