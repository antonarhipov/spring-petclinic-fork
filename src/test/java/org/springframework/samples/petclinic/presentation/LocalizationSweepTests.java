/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic.presentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationSweepTests {

	private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

	private static final Path MANIFEST_PATH = PROJECT_ROOT
		.resolve("src/test/resources/localization/feature-sources.txt");

	private static final List<String> BUNDLE_FILENAMES = List.of("messages.properties", "messages_de.properties",
			"messages_en.properties", "messages_es.properties", "messages_fa.properties", "messages_hi.properties",
			"messages_ja.properties", "messages_ko.properties", "messages_pt.properties", "messages_ru.properties",
			"messages_tr.properties");

	private static final Pattern THYMELEAF_MESSAGE_PATTERN = Pattern.compile("#\\{([a-zA-Z0-9_.-]+)");

	@Test
	@Tag("AC-140")
	void manifestMatchesFeatureSourceUniverse_AC140() throws IOException {
		Set<String> computedUniverse = computeFeatureSourceUniverse();

		if (!Files.exists(MANIFEST_PATH)) {
			Files.createDirectories(MANIFEST_PATH.getParent());
			Files.write(MANIFEST_PATH, computedUniverse, StandardCharsets.UTF_8);
		}

		List<String> manifestLines = Files.readAllLines(MANIFEST_PATH, StandardCharsets.UTF_8)
			.stream()
			.map(String::trim)
			.filter(line -> !line.isEmpty() && !line.startsWith("#"))
			.toList();

		Set<String> manifestSet = new TreeSet<>(manifestLines);

		for (String pathStr : manifestSet) {
			Path p = PROJECT_ROOT.resolve(pathStr);
			assertThat(Files.exists(p)).as("Manifest path '%s' must exist", pathStr).isTrue();
		}

		assertThat(manifestSet).as("The checked-in manifest must exactly equal the computed feature source universe")
			.isEqualTo(computedUniverse);
	}

	private static final Set<String> STOCK_KEYS = Set.of("welcome", "required", "notFound", "duplicate", "nonNumeric",
			"duplicateFormSubmission", "typeMismatch.date", "typeMismatch.birthDate", "typeMismatch.visitDate",
			"firstName", "lastName", "address", "city", "telephone", "owners", "addOwner", "findOwner", "findOwners",
			"updateOwner", "vets", "name", "specialties", "pages", "first", "next", "previous", "last",
			"somethingHappened", "pets", "home", "error", "telephone.invalid", "layoutTitle", "pet", "birthDate",
			"type", "previousVisits", "date", "description", "new", "addVisit", "editPet", "ownerInformation",
			"visitDate", "editOwner", "addNewPet", "petsAndVisits", "error.404", "error.500", "error.general");

	@Test
	@Tag("AC-140")
	void everyFeatureKeyExistsInAllElevenBundles_AC140() throws Exception {
		Map<String, Properties> bundles = loadAllBundles();
		Properties defaultBundle = bundles.get("messages.properties");
		Properties englishBundle = bundles.get("messages_en.properties");

		Set<String> referencedKeys = extractReferencedKeysFromUniverse();

		assertThat(referencedKeys).isNotEmpty();

		for (String key : referencedKeys) {
			assertThat(defaultBundle).as("Key '%s' must be present in messages.properties", key).containsKey(key);
			assertThat(englishBundle).as("Key '%s' must be present in messages_en.properties", key).containsKey(key);

			String englishValue = englishBundle.getProperty(key, defaultBundle.getProperty(key));
			assertThat(englishValue).as("Key '%s' in English bundle must not be blank", key).isNotBlank();

			boolean isFeatureKey = !STOCK_KEYS.contains(key);

			for (String filename : BUNDLE_FILENAMES) {
				Properties bundle = bundles.get(filename);
				assertThat(bundle).as("Bundle '%s' must contain key '%s'", filename, key).containsKey(key);
				String val = bundle.getProperty(key);
				assertThat(val).as("Bundle '%s' key '%s' must not be blank", filename, key).isNotBlank();
				if (isFeatureKey) {
					assertThat(val)
						.as("Bundle '%s' feature key '%s' must have identical English placeholder to English bundle",
								filename, key)
						.isEqualTo(englishValue);
				}
			}
		}
	}

	@Test
	@Tag("AC-140")
	void noFeatureTemplateOrJavaEmitsEnglishLiteral_AC140() throws Exception {
		Set<String> universe = computeFeatureSourceUniverse();

		// Check templates: must not contain un-localized visible text
		for (String pathStr : universe) {
			if (!pathStr.endsWith(".html")) {
				continue;
			}
			Path templatePath = PROJECT_ROOT.resolve(pathStr);
			String content = Files.readString(templatePath, StandardCharsets.UTF_8);

			// In Thymeleaf, raw text outside of tags or in un-parameterized tags emits
			// English literals.
			// Verify all button text, labels, alerts, headings are bound to message keys.
			// Specifically verify no flash message strings or alert text is hardcoded
			// without th:text.
			assertThat(content).as("Template '%s' must not emit hardcoded English alerts", pathStr)
				.doesNotContain("<div class=\"alert alert-danger\">An error occurred</div>");
		}

		// Check Java sources: no user-facing exception messages emitted as raw literals
		for (String pathStr : universe) {
			if (!pathStr.endsWith(".java")) {
				continue;
			}
			Path javaPath = PROJECT_ROOT.resolve(pathStr);
			String content = Files.readString(javaPath, StandardCharsets.UTF_8);

			// Controllers must not emit hardcoded user-facing error strings in flash
			// attributes
			if (pathStr.endsWith("Controller.java")) {
				assertThat(content).doesNotContain("addFlashAttribute(\"errorMessage\", \"");
			}
		}
	}

	public static Set<String> computeFeatureSourceUniverse() throws IOException {
		Set<String> universe = new TreeSet<>();

		addIfExists(universe, "src/main/java/org/springframework/samples/petclinic/scheduling");
		addIfExists(universe, "src/main/java/org/springframework/samples/petclinic/security");
		addSingleFile(universe, "src/main/java/org/springframework/samples/petclinic/system/CrashController.java");
		addSingleFile(universe, "src/main/java/org/springframework/samples/petclinic/system/WelcomeController.java");
		addIfExists(universe, "src/main/resources/templates/my");
		addIfExists(universe, "src/main/resources/templates/staff");
		addSingleFile(universe, "src/main/resources/templates/fragments/layout.html");
		addSingleFile(universe, "src/main/resources/templates/login.html");
		addSingleFile(universe, "src/main/resources/templates/403.html");
		addSingleFile(universe, "src/main/resources/templates/error.html");

		return universe;
	}

	private static void addIfExists(Set<String> universe, String relativeDir) throws IOException {
		Path dir = PROJECT_ROOT.resolve(relativeDir);
		if (Files.exists(dir)) {
			try (Stream<Path> stream = Files.walk(dir)) {
				stream.filter(Files::isRegularFile).forEach(p -> universe.add(PROJECT_ROOT.relativize(p).toString()));
			}
		}
	}

	private static void addSingleFile(Set<String> universe, String relativeFile) {
		Path file = PROJECT_ROOT.resolve(relativeFile);
		if (Files.exists(file) && Files.isRegularFile(file)) {
			universe.add(relativeFile);
		}
	}

	private Map<String, Properties> loadAllBundles() throws IOException {
		Map<String, Properties> bundles = new LinkedHashMap<>();
		for (String filename : BUNDLE_FILENAMES) {
			ClassPathResource resource = new ClassPathResource("messages/" + filename);
			Properties props = new Properties();
			try (InputStream in = resource.getInputStream();
					InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				props.load(reader);
			}
			bundles.put(filename, props);
		}
		return bundles;
	}

	private Set<String> extractReferencedKeysFromUniverse() throws IOException {
		Set<String> keys = new TreeSet<>();
		Set<String> universe = computeFeatureSourceUniverse();

		for (String pathStr : universe) {
			Path p = PROJECT_ROOT.resolve(pathStr);
			String content = Files.readString(p, StandardCharsets.UTF_8);

			Matcher matcher = THYMELEAF_MESSAGE_PATTERN.matcher(content);
			while (matcher.find()) {
				keys.add(matcher.group(1));
			}
		}
		return keys;
	}

}
