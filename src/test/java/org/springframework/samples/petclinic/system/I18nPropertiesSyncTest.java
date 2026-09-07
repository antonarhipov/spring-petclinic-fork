package org.springframework.samples.petclinic.system;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * This test ensures that there are no hard-coded strings without internationalization in
 * any HTML files. Also ensures that a string is translated in every language to avoid
 * partial translations.
 *
 * @author Anuj Ashok Potdar
 */
public class I18nPropertiesSyncTest {

	private static final String I18N_DIR = "src/main/resources";

	private static final String BASE_NAME = "messages";

	public static final String PROPERTIES = ".properties";

	private static final Set<String> EXPECTED_BUNDLES = Set.of("messages.properties", "messages_de.properties",
			"messages_en.properties", "messages_es.properties", "messages_fa.properties", "messages_hi.properties",
			"messages_ja.properties", "messages_ko.properties", "messages_pt.properties", "messages_ru.properties",
			"messages_tr.properties");

	private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

	private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("<(script|style)\\b.*?</\\1>",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern HTML_TEXT_LITERAL = Pattern.compile(">([^<>{}]+)<", Pattern.DOTALL);

	private static final Pattern USER_VISIBLE_ATTRIBUTE = Pattern.compile(
			"(?<![:\\w-])(placeholder|title|alt|aria-label)\\s*=\\s*([\\\"'])(.*?)\\2",
			Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

	private static final Pattern THYMELEAF_TEXT_ATTRIBUTE = Pattern.compile("\\bth:(?:u)?text\\s*=",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern MESSAGE_REFERENCE = Pattern.compile("#\\{([A-Za-z][A-Za-z0-9_.-]*)");

	private static final Pattern MESSAGE_KEY_LITERAL = Pattern.compile("\"(scheduling\\.[A-Za-z0-9_.-]+)\"");

	private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

	private static final Pattern FLASH_ATTRIBUTE_CALL = Pattern.compile("addFlashAttribute\\s*\\((.*?)\\)",
			Pattern.DOTALL);

	private static final Pattern VALIDATION_CALL = Pattern.compile("\\b(rejectValue|reject)\\s*\\((.*?)\\);",
			Pattern.DOTALL);

	private static final Pattern USER_MESSAGE_LITERAL = Pattern
		.compile("(?:sendError|ResponseStatusException)\\s*\\([^,]+,\\s*\"([^\"]*[A-Za-z][^\"]*)\"|"
				+ "\\.body\\s*\\(\\s*\"([^\"]*[A-Za-z][^\"]*)\"\\s*\\)", Pattern.DOTALL);

	private static final Pattern LETTER = Pattern.compile("\\p{L}");

	@Test
	void checkNonInternationalizedStrings() throws Exception {
		Path root = Path.of("src/main");
		List<Path> files;
		Set<String> messageKeys = loadProperties(Path.of(I18N_DIR, "messages", BASE_NAME + PROPERTIES))
			.stringPropertyNames();

		try (Stream<Path> stream = Files.walk(root)) {
			files = stream.filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".html"))
				.filter(p -> !p.toString().contains("/test/"))
				.filter(p -> !p.getFileName().toString().endsWith("Test.java"))
				.toList();
		}

		StringBuilder report = new StringBuilder();

		for (Path file : files) {
			String source = Files.readString(file);
			if (file.toString().endsWith(".html")) {
				inspectHtml(file, source, messageKeys, report);
			}
			else {
				inspectJava(file, source, messageKeys, report);
			}
		}

		if (!report.isEmpty()) {
			fail("Hardcoded (non-internationalized) strings found:\n" + report);
		}
	}

	@Test
	void checkI18nPropertyFilesAreInSync() throws Exception {
		List<Path> propertyFiles;
		try (Stream<Path> stream = Files.walk(Path.of(I18N_DIR))) {
			propertyFiles = stream.filter(p -> p.getFileName().toString().startsWith(BASE_NAME))
				.filter(p -> p.getFileName().toString().endsWith(PROPERTIES))
				.toList();
		}

		Map<String, Properties> localeToProps = new HashMap<>();

		for (Path path : propertyFiles) {
			localeToProps.put(path.getFileName().toString(), loadProperties(path));
		}

		Set<String> actualBundles = new TreeSet<>(localeToProps.keySet());
		if (!actualBundles.equals(new TreeSet<>(EXPECTED_BUNDLES))) {
			fail("Expected exactly the eleven shipped message bundles " + new TreeSet<>(EXPECTED_BUNDLES)
					+ " but found " + actualBundles);
		}

		String baseFile = BASE_NAME + PROPERTIES;
		Properties baseProps = localeToProps.get(baseFile);
		if (baseProps == null) {
			fail("Base properties file '" + baseFile + "' not found.");
			return;
		}

		Set<String> baseKeys = baseProps.stringPropertyNames();
		StringBuilder report = new StringBuilder();

		for (Map.Entry<String, Properties> entry : localeToProps.entrySet()) {
			String fileName = entry.getKey();
			if (fileName.equals(baseFile)) {
				continue;
			}

			Properties props = entry.getValue();
			Set<String> missingKeys = new TreeSet<>(baseKeys);
			missingKeys.removeAll(props.stringPropertyNames());
			Set<String> extraKeys = new TreeSet<>(props.stringPropertyNames());
			extraKeys.removeAll(baseKeys);

			if (!missingKeys.isEmpty()) {
				report.append("Missing keys in ").append(fileName).append(":\n");
				missingKeys.forEach(k -> report.append("  ").append(k).append("\n"));
			}
			if (!extraKeys.isEmpty()) {
				report.append("Extra keys in ").append(fileName).append(":\n");
				extraKeys.forEach(k -> report.append("  ").append(k).append("\n"));
			}
		}

		if (!report.isEmpty()) {
			fail("Translation files are not in sync:\n" + report);
		}
	}

	private void inspectHtml(Path file, String source, Set<String> messageKeys, StringBuilder report) {
		String visibleSource = SCRIPT_OR_STYLE.matcher(HTML_COMMENT.matcher(source).replaceAll("")).replaceAll("");
		var textMatcher = HTML_TEXT_LITERAL.matcher(visibleSource);
		while (textMatcher.find()) {
			String text = textMatcher.group(1).trim();
			int openingBracket = visibleSource.lastIndexOf('<', textMatcher.start());
			String precedingTag = openingBracket >= 0 ? visibleSource.substring(openingBracket, textMatcher.start() + 1)
					: "";
			if (isUserVisibleLiteral(text) && !THYMELEAF_TEXT_ATTRIBUTE.matcher(precedingTag).find()) {
				appendFinding(report, "HTML text", file, source, textMatcher.start(1), text);
			}
		}

		var attributeMatcher = USER_VISIBLE_ATTRIBUTE.matcher(visibleSource);
		while (attributeMatcher.find()) {
			String value = attributeMatcher.group(3).trim();
			if (isUserVisibleLiteral(value) && !containsTemplateExpression(value)) {
				appendFinding(report, "HTML attribute " + attributeMatcher.group(1), file, source,
						attributeMatcher.start(3), value);
			}
		}

		var messageMatcher = MESSAGE_REFERENCE.matcher(source);
		while (messageMatcher.find()) {
			String key = messageMatcher.group(1);
			if (!messageKeys.contains(key)) {
				appendFinding(report, "Unknown template message key", file, source, messageMatcher.start(1), key);
			}
		}
	}

	private void inspectJava(Path file, String source, Set<String> messageKeys, StringBuilder report) {
		var keyMatcher = MESSAGE_KEY_LITERAL.matcher(source);
		while (keyMatcher.find()) {
			if (!messageKeys.contains(keyMatcher.group(1))) {
				appendFinding(report, "Unknown Java message key", file, source, keyMatcher.start(1),
						keyMatcher.group(1));
			}
		}

		var flashMatcher = FLASH_ATTRIBUTE_CALL.matcher(source);
		while (flashMatcher.find()) {
			List<String> literals = stringLiterals(flashMatcher.group(1));
			if (literals.size() >= 2 && !messageKeys.contains(literals.get(1))) {
				appendFinding(report, "Hard-coded flash message", file, source, flashMatcher.start(), literals.get(1));
			}
		}

		var validationMatcher = VALIDATION_CALL.matcher(source);
		while (validationMatcher.find()) {
			List<String> literals = stringLiterals(validationMatcher.group(2));
			int codeIndex = validationMatcher.group(1).equals("rejectValue") ? 1 : 0;
			if (literals.size() > codeIndex && !messageKeys.contains(literals.get(codeIndex))) {
				appendFinding(report, "Unknown validation message key", file, source, validationMatcher.start(),
						literals.get(codeIndex));
			}
			int minimumLiteralCount = validationMatcher.group(1).equals("rejectValue") ? 3 : 2;
			if (literals.size() >= minimumLiteralCount && isUserVisibleLiteral(literals.get(literals.size() - 1))) {
				appendFinding(report, "Hard-coded validation message", file, source, validationMatcher.start(),
						literals.get(literals.size() - 1));
			}
		}

		var statusMatcher = USER_MESSAGE_LITERAL.matcher(source);
		while (statusMatcher.find()) {
			String value = statusMatcher.group(1) != null ? statusMatcher.group(1) : statusMatcher.group(2);
			appendFinding(report, "Hard-coded status message", file, source, statusMatcher.start(), value);
		}
	}

	private List<String> stringLiterals(String source) {
		List<String> values = new ArrayList<>();
		var matcher = JAVA_STRING_LITERAL.matcher(source);
		while (matcher.find()) {
			values.add(matcher.group(1));
		}
		return values;
	}

	private boolean isUserVisibleLiteral(String value) {
		String withoutEntities = value.replaceAll("&[A-Za-z]+;", "");
		return !containsTemplateExpression(value) && LETTER.matcher(withoutEntities).find();
	}

	private boolean containsTemplateExpression(String value) {
		return value.contains("#{") || value.contains("${") || value.contains("*{") || value.contains("@{")
				|| value.contains("[[") || value.contains("[(");
	}

	private void appendFinding(StringBuilder report, String kind, Path file, String source, int offset, String value) {
		long line = source.substring(0, Math.min(offset, source.length()))
			.chars()
			.filter(character -> character == '\n')
			.count() + 1;
		report.append(kind)
			.append(": ")
			.append(file)
			.append(" Line ")
			.append(line)
			.append(": ")
			.append(value)
			.append('\n');
	}

	private Properties loadProperties(Path path) throws Exception {
		Properties props = new Properties();
		try (var reader = Files.newBufferedReader(path)) {
			props.load(reader);
		}
		return props;
	}

}
