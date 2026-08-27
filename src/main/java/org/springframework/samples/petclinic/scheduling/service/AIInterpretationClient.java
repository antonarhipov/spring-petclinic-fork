package org.springframework.samples.petclinic.scheduling.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto;
import org.springframework.samples.petclinic.scheduling.dto.AIInterpretationResponseDto.TimeWindowDto;
import org.springframework.samples.petclinic.scheduling.model.ClinicSettings;
import org.springframework.stereotype.Service;

@Service
public class AIInterpretationClient {

	private static final Logger log = LoggerFactory.getLogger(AIInterpretationClient.class);

	private static final Set<String> ALLOWED_ISSUES = Set.of("INCOMPLETE_AVAILABILITY",
			"CONTRADICTORY_CLINICAL_ROUTING", "UNSAFE_CONTENT");

	private static final Set<String> RESPONSE_FIELDS = Set.of("summary", "careType", "specialtyName",
			"preferredVetName", "durationMinutes", "urgent", "windows", "issues");

	private static final Set<String> REQUIRED_RESPONSE_FIELDS = Set.of("summary", "careType", "urgent", "windows",
			"issues");

	private static final Set<String> WINDOW_FIELDS = Set.of("dayOfWeek", "startTime", "endTime", "namedPeriod", "kind",
			"dateExpression", "explicitDate", "weekOffset", "monthOffset", "month", "ordinalWeek");

	private static final Set<String> WINDOW_KINDS = Set.of("ALLOWED", "PREFERRED", "EXCLUDED");

	private static final Set<String> DATE_EXPRESSIONS = Set.of("ANY_DATE", "EXPLICIT_DATE", "RELATIVE_WEEK",
			"END_OF_WEEK", "RELATIVE_MONTH", "NAMED_MONTH");

	private static final Set<String> DAYS = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
			"SUNDAY");

	private static final Set<String> MONTHS = Set.of("JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY",
			"AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER");

	private static final Set<String> ORDINAL_WEEKS = Set.of("FIRST", "SECOND", "THIRD", "FOURTH", "LAST");

	private static final Set<String> NAMED_PERIODS = Set.of("MORNING", "AFTERNOON", "EVENING");

	private static final String RESPONSE_SCHEMA = """
			{
			  "type": "object",
			  "additionalProperties": false,
			  "required": ["summary", "careType", "urgent", "windows", "issues"],
			  "properties": {
			    "summary": {"type": "string", "minLength": 1},
			    "careType": {"type": "string", "enum": ["GENERAL", "SPECIALTY"]},
			    "specialtyName": {"type": ["string", "null"]},
			    "preferredVetName": {"type": ["string", "null"]},
			    "durationMinutes": {"type": ["integer", "null"]},
			    "urgent": {"type": "boolean"},
			    "windows": {
			      "type": "array",
			      "items": {
			        "type": "object",
			        "additionalProperties": false,
			        "required": ["kind", "dateExpression"],
			        "properties": {
			          "kind": {"type": "string", "enum": ["ALLOWED", "PREFERRED", "EXCLUDED"]},
			          "dateExpression": {"type": "string", "enum": ["ANY_DATE", "EXPLICIT_DATE", "RELATIVE_WEEK", "END_OF_WEEK", "RELATIVE_MONTH", "NAMED_MONTH"]},
			          "explicitDate": {"type": ["string", "null"], "format": "date"},
			          "dayOfWeek": {"type": ["string", "null"], "enum": [null, "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]},
			          "weekOffset": {"type": ["integer", "null"], "minimum": 0},
			          "monthOffset": {"type": ["integer", "null"], "minimum": 0},
			          "month": {"type": ["string", "null"], "enum": [null, "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE", "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"]},
			          "ordinalWeek": {"type": ["string", "null"], "enum": [null, "FIRST", "SECOND", "THIRD", "FOURTH", "LAST"]},
			          "startTime": {"type": ["string", "null"]},
			          "endTime": {"type": ["string", "null"]},
			          "namedPeriod": {"type": ["string", "null"], "enum": [null, "MORNING", "AFTERNOON", "EVENING"]}
			        }
			      }
			    },
			    "issues": {
			      "type": "array",
			      "items": {
			        "type": "string",
			        "enum": ["INCOMPLETE_AVAILABILITY", "CONTRADICTORY_CLINICAL_ROUTING", "UNSAFE_CONTENT"]
			      }
			    }
			  }
			}
			""";

	private final ChatModel chatModel;

	private final ObjectMapper objectMapper;

	private final SchedulingTimeService timeService;

	@Autowired
	public AIInterpretationClient(ObjectProvider<ChatModel> chatModelProvider,
			ObjectProvider<ObjectMapper> objectMapperProvider, SchedulingTimeService timeService) {
		this(chatModelProvider.getIfAvailable(), objectMapperProvider.getIfAvailable(), timeService);
	}

	public AIInterpretationClient(ChatModel chatModel, ObjectMapper objectMapper, SchedulingTimeService timeService) {
		this.chatModel = chatModel;
		this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
		this.timeService = timeService;
	}

	public AIInterpretationClient(ChatModel chatModel, ObjectMapper objectMapper) {
		this(chatModel, objectMapper, null);
	}

	public AIInterpretationResponseDto interpret(String text, String petTypeName, ClinicSettings settings,
			List<String> activeVetNames, List<String> activeSpecialties) {

		if (chatModel == null) {
			log.info("ChatModel not available; using heuristic fallback interpretation");
			return fallbackInterpretation(text, settings, activeVetNames, activeSpecialties);
		}

		try {
			String promptText = buildPrompt(text, petTypeName, settings, activeVetNames, activeSpecialties);
			if (!(chatModel.getOptions() instanceof OllamaChatOptions configuredOptions)) {
				throw new IllegalStateException("Ollama chat options are required for AI interpretation");
			}
			OllamaChatOptions options = configuredOptions.mutate()
				.outputSchema(RESPONSE_SCHEMA)
				.temperature(0.0)
				.build();
			log.info("Submitting AI interpretation request to model {}:\n{}", options.getModel(), promptText);
			String response = chatModel.call(new Prompt(promptText, options)).getResult().getOutput().getText();
			log.info("Received AI interpretation response from model {}:\n{}", options.getModel(), response);
			AIInterpretationResponseDto result = parseResponse(response, settings, activeVetNames, activeSpecialties);
			result = removeSpuriousRoutingContradiction(result, text, activeSpecialties);
			log.info(
					"Bound AI interpretation response: careType={}, durationMinutes={}, urgent={}, windowCount={}, issues={}",
					result.careType(), result.durationMinutes(), result.urgent(), result.windows().size(),
					result.issues());
			return result;
		}
		catch (Exception ex) {
			log.warn("AI interpretation failed: {}", ex.getMessage());
			throw new RuntimeException("AI interpretation failure", ex);
		}
	}

	private String buildPrompt(String text, String petTypeName, ClinicSettings settings, List<String> activeVetNames,
			List<String> activeSpecialties) {
		String clinicDate = timeService != null ? timeService.today().toString() : "unknown";
		String clinicZone = timeService != null ? timeService.getClinicZoneId().getId() : "UTC";
		return "You are an appointment scheduling assistant for a veterinary clinic. "
				+ "Analyze the following pet visit description and availability request.\n" + "Clinic settings:\n"
				+ "- Current clinic date: " + clinicDate + "\n" + "- Clinic time zone: " + clinicZone + "\n"
				+ "- Default duration: " + settings.getDefaultDurationMinutes() + " minutes\n" + "- Min duration: "
				+ settings.getMinDurationMinutes() + " minutes\n" + "- Max duration: "
				+ settings.getMaxDurationMinutes() + " minutes\n" + "- Morning: " + settings.getMorningStartTime()
				+ " to " + settings.getMorningEndTime() + "\n" + "- Afternoon: " + settings.getAfternoonStartTime()
				+ " to " + settings.getAfternoonEndTime() + "\n" + "- Evening: " + settings.getEveningStartTime()
				+ " to " + settings.getEveningEndTime() + "\n" + "- Active veterinarians: "
				+ String.join(", ", activeVetNames) + "\n" + "- Active specialties: "
				+ String.join(", ", activeSpecialties) + "\n" + "- Pet type: "
				+ (petTypeName != null ? petTypeName : "pet") + "\n" + "Pet owner text: \"" + text + "\"\n"
				+ "Extract scheduling facts only; do not diagnose or infer a specialty from symptoms. "
				+ "Use GENERAL unless the owner explicitly names one of the active specialties. "
				+ "Symptoms such as a running nose or watery eyes are ordinary GENERAL visit reasons and never imply a routing contradiction. "
				+ "Use CONTRADICTORY_CLINICAL_ROUTING only when the owner explicitly gives mutually incompatible routing instructions; never use it because a symptom might benefit from specialist review. "
				+ "Return calendar language as symbolic expressions; never calculate a relative date. Supported dateExpression values are: "
				+ "ANY_DATE for a bare weekday or time, EXPLICIT_DATE for an explicitly stated calendar date, RELATIVE_WEEK for this/next/in-N weeks, "
				+ "END_OF_WEEK for the end of a working week, RELATIVE_MONTH for this/next/in-N months, and NAMED_MONTH for a named month. "
				+ "Use weekOffset 0 for this week, 1 for next week, and N for in N weeks. Use monthOffset the same way for months. "
				+ "Use ordinalWeek FIRST, SECOND, THIRD, FOURTH, or LAST for a week of a month. "
				+ "A bare weekday uses ANY_DATE. 'Next week Thursday' uses RELATIVE_WEEK, weekOffset 1, dayOfWeek THURSDAY. "
				+ "Unqualified availability is ALLOWED. Use PREFERRED only for explicit words such as prefer or preferably, and EXCLUDED for unavailable time. "
				+ "Map 'after lunch' to namedPeriod AFTERNOON. If calendar language is unsupported or incomplete, return INCOMPLETE_AVAILABILITY rather than guessing. "
				+ "Respond with a JSON object strictly matching this shape: "
				+ "{\"summary\": string, \"careType\": \"GENERAL\"|\"SPECIALTY\", \"specialtyName\": string|null, "
				+ "\"preferredVetName\": string|null, \"durationMinutes\": int, \"urgent\": boolean, "
				+ "\"windows\": [{\"kind\": \"ALLOWED\"|\"PREFERRED\"|\"EXCLUDED\", \"dateExpression\": string, \"explicitDate\": string|null, \"dayOfWeek\": string|null, \"weekOffset\": int|null, \"monthOffset\": int|null, \"month\": string|null, \"ordinalWeek\": string|null, \"startTime\": string|null, \"endTime\": string|null, \"namedPeriod\": string|null}], "
				+ "\"issues\": [string]}";
	}

	private AIInterpretationResponseDto removeSpuriousRoutingContradiction(AIInterpretationResponseDto result,
			String text, List<String> activeSpecialties) {
		if (!"GENERAL".equals(result.careType()) || result.specialtyName() != null
				|| !result.issues().contains("CONTRADICTORY_CLINICAL_ROUTING")
				|| containsExplicitRoutingInstruction(text, activeSpecialties)) {
			return result;
		}
		List<String> correctedIssues = result.issues()
			.stream()
			.filter(issue -> !"CONTRADICTORY_CLINICAL_ROUTING".equals(issue))
			.toList();
		log.info("Removed unsupported CONTRADICTORY_CLINICAL_ROUTING issue from GENERAL interpretation");
		return new AIInterpretationResponseDto(result.summary(), result.careType(), result.specialtyName(),
				result.preferredVetName(), result.durationMinutes(), result.urgent(), result.windows(),
				correctedIssues);
	}

	private boolean containsExplicitRoutingInstruction(String text, List<String> activeSpecialties) {
		String normalizedText = text.toLowerCase(Locale.ROOT);
		if (normalizedText.contains("specialist") || normalizedText.contains("specialty")
				|| normalizedText.contains("general vet") || normalizedText.contains("general care")) {
			return true;
		}
		return activeSpecialties.stream()
			.map(specialty -> specialty.toLowerCase(Locale.ROOT))
			.anyMatch(normalizedText::contains);
	}

	public AIInterpretationResponseDto parseResponse(String rawJson, ClinicSettings settings,
			List<String> activeVetNames, List<String> activeSpecialties) {
		if (rawJson == null || rawJson.isBlank()) {
			return new AIInterpretationResponseDto("Consultation request", "GENERAL", null, null,
					settings.getDefaultDurationMinutes(), false, Collections.emptyList(), List.of("INVALID_AI_OUTPUT"));
		}

		String json = extractJson(rawJson);
		try {
			JsonNode root = objectMapper.readTree(json);
			if (!hasValidStructure(root)) {
				return new AIInterpretationResponseDto("Consultation request", "GENERAL", null, null,
						settings.getDefaultDurationMinutes(), false, Collections.emptyList(),
						List.of("INVALID_AI_OUTPUT"));
			}

			String summary = root.get("summary").asText();
			String careType = root.get("careType").asText();
			String specialtyName = root.hasNonNull("specialtyName") ? root.get("specialtyName").asText() : null;
			String preferredVetName = root.hasNonNull("preferredVetName") ? root.get("preferredVetName").asText()
					: null;
			Integer durationMinutes = root.hasNonNull("durationMinutes")
					&& root.get("durationMinutes").isIntegralNumber() ? root.get("durationMinutes").asInt()
							: settings.getDefaultDurationMinutes();
			boolean urgent = root.hasNonNull("urgent") && root.get("urgent").asBoolean();

			List<TimeWindowDto> windows = new ArrayList<>();
			if (root.has("windows") && root.get("windows").isArray()) {
				for (JsonNode wNode : root.get("windows")) {
					String kind = wNode.get("kind").asText();
					String dateExpression = wNode.get("dateExpression").asText();
					String explicitDate = wNode.hasNonNull("explicitDate") ? wNode.get("explicitDate").asText() : null;
					String dayOfWeek = wNode.hasNonNull("dayOfWeek") ? wNode.get("dayOfWeek").asText() : null;
					Integer weekOffset = wNode.hasNonNull("weekOffset") ? wNode.get("weekOffset").asInt() : null;
					Integer monthOffset = wNode.hasNonNull("monthOffset") ? wNode.get("monthOffset").asInt() : null;
					String month = wNode.hasNonNull("month") ? wNode.get("month").asText() : null;
					String ordinalWeek = wNode.hasNonNull("ordinalWeek") ? wNode.get("ordinalWeek").asText() : null;
					String startTime = wNode.hasNonNull("startTime") ? wNode.get("startTime").asText() : null;
					String endTime = wNode.hasNonNull("endTime") ? wNode.get("endTime").asText() : null;
					String namedPeriod = wNode.hasNonNull("namedPeriod") ? wNode.get("namedPeriod").asText() : null;
					windows.add(new TimeWindowDto(kind, dateExpression, explicitDate, dayOfWeek, weekOffset,
							monthOffset, month, ordinalWeek, startTime, endTime, namedPeriod));
				}
			}

			List<String> issues = new ArrayList<>();
			if (root.has("issues") && root.get("issues").isArray()) {
				for (JsonNode iNode : root.get("issues")) {
					String issue = iNode.asText();
					if (ALLOWED_ISSUES.contains(issue)) {
						issues.add(issue);
					}
					else {
						issues.add("INVALID_AI_OUTPUT");
					}
				}
			}

			return new AIInterpretationResponseDto(summary, careType, specialtyName, preferredVetName, durationMinutes,
					urgent, windows, issues);
		}
		catch (Exception ex) {
			log.warn("Failed to parse AI JSON response: {}", ex.getMessage());
			return new AIInterpretationResponseDto("Consultation request", "GENERAL", null, null,
					settings.getDefaultDurationMinutes(), false, Collections.emptyList(), List.of("INVALID_AI_OUTPUT"));
		}
	}

	private boolean hasValidStructure(JsonNode root) {
		if (root == null || !root.isObject() || !hasOnlyFields(root, RESPONSE_FIELDS)
				|| !REQUIRED_RESPONSE_FIELDS.stream().allMatch(root::has)) {
			return false;
		}
		if (!root.get("summary").isTextual() || root.get("summary").asText().isBlank()
				|| !root.get("careType").isTextual()
				|| !("GENERAL".equals(root.get("careType").asText())
						|| "SPECIALTY".equals(root.get("careType").asText()))
				|| !isNullableText(root.get("specialtyName")) || !isNullableText(root.get("preferredVetName"))
				|| (root.has("durationMinutes") && !root.get("durationMinutes").isNull()
						&& !root.get("durationMinutes").isIntegralNumber())
				|| !root.get("urgent").isBoolean() || !root.get("windows").isArray() || !root.get("issues").isArray()) {
			return false;
		}
		for (JsonNode window : root.get("windows")) {
			if (!window.isObject() || !hasOnlyFields(window, WINDOW_FIELDS) || !window.has("kind")
					|| !window.get("kind").isTextual() || !WINDOW_KINDS.contains(window.get("kind").asText())
					|| !window.has("dateExpression") || !window.get("dateExpression").isTextual()
					|| !DATE_EXPRESSIONS.contains(window.get("dateExpression").asText())
					|| !isNullableText(window.get("explicitDate")) || !isNullableText(window.get("dayOfWeek"))
					|| !isNullableText(window.get("startTime")) || !isNullableText(window.get("endTime"))
					|| !isNullableText(window.get("namedPeriod")) || !isNullableText(window.get("month"))
					|| !isNullableText(window.get("ordinalWeek")) || !isNullableNonNegativeInt(window.get("weekOffset"))
					|| !isNullableNonNegativeInt(window.get("monthOffset")) || !hasValidWindowEnums(window)
					|| !hasValidWindowCombination(window)) {
				return false;
			}
		}
		for (JsonNode issue : root.get("issues")) {
			if (!issue.isTextual() || !ALLOWED_ISSUES.contains(issue.asText())) {
				return false;
			}
		}
		return true;
	}

	private boolean hasOnlyFields(JsonNode node, Set<String> allowedFields) {
		return node.propertyStream().allMatch(property -> allowedFields.contains(property.getKey()));
	}

	private boolean isNullableText(JsonNode node) {
		return node == null || node.isNull() || node.isTextual();
	}

	private boolean isNullableNonNegativeInt(JsonNode node) {
		return node == null || node.isNull()
				|| (node.isIntegralNumber() && node.canConvertToInt() && node.asInt() >= 0);
	}

	private boolean hasValidWindowEnums(JsonNode window) {
		return (!window.hasNonNull("dayOfWeek") || DAYS.contains(window.get("dayOfWeek").asText()))
				&& (!window.hasNonNull("month") || MONTHS.contains(window.get("month").asText()))
				&& (!window.hasNonNull("ordinalWeek") || ORDINAL_WEEKS.contains(window.get("ordinalWeek").asText()))
				&& (!window.hasNonNull("namedPeriod") || NAMED_PERIODS.contains(window.get("namedPeriod").asText()));
	}

	private boolean hasValidWindowCombination(JsonNode window) {
		boolean explicitDate = window.hasNonNull("explicitDate");
		boolean weekOffset = window.hasNonNull("weekOffset");
		boolean monthOffset = window.hasNonNull("monthOffset");
		boolean month = window.hasNonNull("month");
		boolean ordinal = window.hasNonNull("ordinalWeek");
		boolean namedPeriod = window.hasNonNull("namedPeriod");
		boolean startTime = window.hasNonNull("startTime");
		boolean endTime = window.hasNonNull("endTime");
		if (namedPeriod && (startTime || endTime) || startTime != endTime) {
			return false;
		}
		return switch (window.get("dateExpression").asText()) {
			case "ANY_DATE" -> !explicitDate && !weekOffset && !monthOffset && !month && !ordinal;
			case "EXPLICIT_DATE" -> explicitDate && !weekOffset && !monthOffset && !month && !ordinal;
			case "RELATIVE_WEEK", "END_OF_WEEK" -> !explicitDate && weekOffset && !monthOffset && !month && !ordinal;
			case "RELATIVE_MONTH" -> !explicitDate && !weekOffset && monthOffset && !month;
			case "NAMED_MONTH" -> !explicitDate && !weekOffset && !monthOffset && month;
			default -> false;
		};
	}

	private String extractJson(String text) {
		String trimmed = text.trim();
		if (trimmed.startsWith("```")) {
			int start = trimmed.indexOf('{');
			int end = trimmed.lastIndexOf('}');
			if (start >= 0 && end > start) {
				return trimmed.substring(start, end + 1);
			}
		}
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return trimmed.substring(start, end + 1);
		}
		return trimmed;
	}

	public AIInterpretationResponseDto fallbackInterpretation(String text, ClinicSettings settings,
			List<String> activeVetNames, List<String> activeSpecialties) {
		String lower = text.toLowerCase();
		boolean urgent = lower.contains("urgent") || lower.contains("emergency") || lower.contains("bleeding")
				|| lower.contains("immediately");

		String preferredVet = null;
		for (String vetName : activeVetNames) {
			if (lower.contains(vetName.toLowerCase())) {
				preferredVet = vetName;
				break;
			}
		}

		String specialty = null;
		String careType = "GENERAL";
		for (String spec : activeSpecialties) {
			if (lower.contains(spec.toLowerCase())) {
				specialty = spec;
				careType = "SPECIALTY";
				break;
			}
		}

		return new AIInterpretationResponseDto(
				"Consultation: " + (text.length() > 50 ? text.substring(0, 47) + "..." : text), careType, specialty,
				preferredVet, settings.getDefaultDurationMinutes(), urgent, Collections.emptyList(),
				Collections.emptyList());
	}

}
