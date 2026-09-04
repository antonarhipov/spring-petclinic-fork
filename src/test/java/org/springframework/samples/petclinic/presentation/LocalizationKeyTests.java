/*
 * Copyright 2012-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 */

package org.springframework.samples.petclinic.presentation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Localization contract for feature-introduced pages, attributes, and menu text. */
class LocalizationKeyTests {

	private static final Path RESOURCES = Path.of("src/main/resources");

	private static final Pattern MESSAGE_REFERENCE = Pattern.compile("#\\{([A-Za-z0-9_.-]+)");

	private static final Pattern VISIBLE_ATTRIBUTE = Pattern
		.compile("(?:placeholder|title|alt|aria-label)=\"([^\"]+)\"");

	private static final Set<String> FEATURE_KEYS = Set.of("username", "password", "login", "logout", "accessDenied",
			"accessDeniedMessage", "invalidCredentials", "loggedOut", "owner", "staff", "myAppointments", "myPets",
			"scheduleAppointment", "schedulingQueue", "calendar", "clinicSettings", "language.english",
			"language.german", "language.spanish", "language.persian", "language.hindi", "language.japanese",
			"language.korean", "language.portuguese", "language.russian", "language.turkish", "noAppointments",
			"status", "startRequest", "queueEmpty", "calendarEmpty", "settingsLater", "urgentCare", "urgentCareMessage",
			"requestNew", "reason", "availability", "selectPet", "createRequest", "requestDetails", "consent",
			"grantConsent", "declineConsent", "interpretation", "estimatedMinutes", "careType", "preferredVet",
			"confirmInterpretation", "suggestion", "acceptSuggestion", "springLogo", "activeRequestUnavailable",
			"requestActionNotAllowed");

	@Test
	void everyReferencedTemplateKeyExistsInTheDefaultBundle() throws IOException {
		Properties defaults = load(RESOURCES.resolve("messages/messages.properties"));
		List<String> missing = new ArrayList<>();
		for (Path template : featureTemplates()) {
			Matcher matcher = MESSAGE_REFERENCE.matcher(Files.readString(template));
			while (matcher.find()) {
				if (!defaults.containsKey(matcher.group(1))) {
					missing.add(template + ": " + matcher.group(1));
				}
			}
		}
		assertThat(missing).isEmpty();
	}

	@Test
	void featurePlaceholdersArePresentAndIdenticalInAllElevenBundles() throws IOException {
		Properties defaults = load(RESOURCES.resolve("messages/messages.properties"));
		List<Path> bundles;
		try (Stream<Path> paths = Files.list(RESOURCES.resolve("messages"))) {
			bundles = paths.filter(path -> path.getFileName().toString().startsWith("messages"))
				.filter(path -> path.toString().endsWith(".properties"))
				.toList();
		}
		assertThat(bundles).hasSize(11);
		for (Path bundle : bundles) {
			Properties localized = load(bundle);
			for (String key : FEATURE_KEYS) {
				assertThat(localized.getProperty(key)).as("%s in %s", key, bundle.getFileName())
					.isEqualTo(defaults.getProperty(key));
			}
		}
	}

	@Test
	void featureTemplatesEmitNoUnkeyedTextOrVisibleAttributes() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path template : featureTemplates()) {
			List<String> lines = Files.readAllLines(template);
			for (int index = 0; index < lines.size(); index++) {
				String line = lines.get(index).trim();
				if (line.matches(".*>[A-Za-z][^<>{}]*<.*") && !line.contains("th:text=") && !line.startsWith("<!--")) {
					violations.add(template + ":" + (index + 1) + " text");
				}
				Matcher attribute = VISIBLE_ATTRIBUTE.matcher(line);
				while (attribute.find()) {
					String value = attribute.group(1);
					String attributeName = line.substring(attribute.start(), line.indexOf('=', attribute.start()));
					if (!value.isBlank() && !value.contains("${") && !value.contains("#{")
							&& !line.contains("th:" + attributeName + "=")) {
						violations.add(template + ":" + (index + 1) + " attribute " + value);
					}
				}
				if (line.matches(".*th:(?:text|title|placeholder|alt)=\"'[^']+'\".*")) {
					violations.add(template + ":" + (index + 1) + " inline literal");
				}
			}
		}
		assertThat(violations).isEmpty();
	}

	private static List<Path> featureTemplates() throws IOException {
		List<Path> templates = new ArrayList<>();
		templates.add(RESOURCES.resolve("templates/fragments/layout.html"));
		templates.add(RESOURCES.resolve("templates/login.html"));
		for (String directory : List.of("my", "staff")) {
			Path root = RESOURCES.resolve("templates").resolve(directory);
			if (Files.exists(root)) {
				try (Stream<Path> paths = Files.walk(root)) {
					templates.addAll(paths.filter(path -> path.toString().endsWith(".html")).toList());
				}
			}
		}
		return templates;
	}

	private static Properties load(Path path) throws IOException {
		Properties properties = new Properties();
		try (var reader = Files.newBufferedReader(path)) {
			properties.load(reader);
		}
		return properties;
	}

}
