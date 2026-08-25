package org.springframework.samples.petclinic.system;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
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

	private static final Pattern HTML_TEXT_LITERAL = Pattern.compile(">([^<>{}]+)<");

	private static final Pattern BRACKET_ONLY = Pattern.compile("<[^>]*>\\s*[\\[\\]](?:&nbsp;)?\\s*</[^>]*>");

	private static final Pattern HAS_TH_TEXT_ATTRIBUTE = Pattern.compile("th:(u)?text\\s*=\\s*(\"[^\"]*\"|'[^']*')");

	@Test
	void checkNonInternationalizedStrings() throws Exception {
		Path root = Path.of("src/main");
		List<Path> files;

		try (Stream<Path> stream = Files.walk(root)) {
			files = stream.filter(p -> p.toString().endsWith(".html"))
				.filter(p -> !p.toString().contains("/test/"))
				.toList();
		}

		StringBuilder report = new StringBuilder();

		for (Path file : files) {
			String content = Files.readString(file);
			int lineNumber = 1;
			int pos = 0;

			while (pos < content.length()) {
				if (content.startsWith("<!--", pos)) {
					int end = content.indexOf("-->", pos);
					if (end == -1) {
						break;
					}
					for (int i = pos; i < end + 3; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = end + 3;
					continue;
				}

				if (content.regionMatches(true, pos, "<script", 0, 7) && (pos + 7 >= content.length()
						|| Character.isWhitespace(content.charAt(pos + 7)) || content.charAt(pos + 7) == '>')) {
					int end = indexOfIgnoreCase(content, "</script>", pos);
					if (end == -1) {
						break;
					}
					for (int i = pos; i < end + 9; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = end + 9;
					continue;
				}

				if (content.regionMatches(true, pos, "<style", 0, 6) && (pos + 6 >= content.length()
						|| Character.isWhitespace(content.charAt(pos + 6)) || content.charAt(pos + 6) == '>')) {
					int end = indexOfIgnoreCase(content, "</style>", pos);
					if (end == -1) {
						break;
					}
					for (int i = pos; i < end + 8; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = end + 8;
					continue;
				}

				if (content.startsWith("</", pos)) {
					int end = content.indexOf('>', pos);
					if (end == -1) {
						break;
					}
					for (int i = pos; i < end + 1; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = end + 1;
					continue;
				}

				if (content.startsWith("<!", pos) || content.startsWith("<?", pos)) {
					int end = content.indexOf('>', pos);
					if (end == -1) {
						break;
					}
					for (int i = pos; i < end + 1; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = end + 1;
					continue;
				}

				if (content.charAt(pos) == '<') {
					int startTag = pos;
					boolean inDoubleQuote = false;
					boolean inSingleQuote = false;
					int endTag = -1;

					for (int i = pos; i < content.length(); i++) {
						char c = content.charAt(i);
						if (c == '"' && !inSingleQuote) {
							inDoubleQuote = !inDoubleQuote;
						}
						else if (c == '\'' && !inDoubleQuote) {
							inSingleQuote = !inSingleQuote;
						}
						else if (c == '>' && !inDoubleQuote && !inSingleQuote) {
							endTag = i;
							break;
						}
					}

					if (endTag == -1) {
						break;
					}

					String tag = content.substring(startTag, endTag + 1);
					for (int i = pos; i < endTag + 1; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = endTag + 1;

					boolean hasI18n = HAS_TH_TEXT_ATTRIBUTE.matcher(tag).find() || tag.contains("#{");

					int textStart = pos;
					int textLine = lineNumber;
					int nextTag = content.indexOf('<', pos);
					if (nextTag == -1) {
						nextTag = content.length();
					}
					String text = content.substring(textStart, nextTag).trim();

					if (!text.isEmpty()) {
						boolean hasBracketsOrExpression = text.contains("#{")
								|| (text.contains("{") && text.contains("}"));
						boolean isBracketOnly = isBracketOrWhitespaceOnly(text);

						if (!hasI18n && !hasBracketsOrExpression && !isBracketOnly) {
							report.append("HTML: ")
								.append(file)
								.append(" Line ")
								.append(textLine)
								.append(": ")
								.append(text)
								.append("\n");
						}
					}

					for (int i = textStart; i < nextTag; i++) {
						if (content.charAt(i) == '\n') {
							lineNumber++;
						}
					}
					pos = nextTag;
					continue;
				}

				if (content.charAt(pos) == '\n') {
					lineNumber++;
				}
				pos++;
			}
		}

		if (!report.isEmpty()) {
			fail("Hardcoded (non-internationalized) strings found:\n" + report);
		}
	}

	private static int indexOfIgnoreCase(String src, String target, int fromIndex) {
		String srcLower = src.toLowerCase();
		String targetLower = target.toLowerCase();
		return srcLower.indexOf(targetLower, fromIndex);
	}

	private static boolean isBracketOrWhitespaceOnly(String text) {
		String cleaned = text.replace("&nbsp;", "").replaceAll("\\s+", "");
		return cleaned.equals("[") || cleaned.equals("]") || cleaned.equals("[]") || cleaned.isEmpty();
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
			Properties props = new Properties();
			try (var reader = Files.newBufferedReader(path)) {
				props.load(reader);
				localeToProps.put(path.getFileName().toString(), props);
			}
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
			// We use fallback logic to include english strings, hence messages_en is not
			// populated.
			if (fileName.equals(baseFile) || "messages_en.properties".equals(fileName)) {
				continue;
			}

			Properties props = entry.getValue();
			Set<String> missingKeys = new TreeSet<>(baseKeys);
			missingKeys.removeAll(props.stringPropertyNames());

			if (!missingKeys.isEmpty()) {
				report.append("Missing keys in ").append(fileName).append(":\n");
				missingKeys.forEach(k -> report.append("  ").append(k).append("\n"));
			}
		}

		if (!report.isEmpty()) {
			fail("Translation files are not in sync:\n" + report);
		}
	}

}
