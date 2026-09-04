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

package org.springframework.samples.petclinic.planning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PlanGuardSupport {

	static final Path TASKS = Path.of("spec/tasks.yaml");

	static final Path STATUS = Path.of("spec/status.md");

	static final Path RULES = Path.of("spec/rules.md");

	private static final Pattern TASK_ID = Pattern.compile("^        - id: (task-\\d+\\.\\d+)$");

	private static final Pattern QUOTED_ITEM = Pattern.compile("^            - \\\"(.*)\\\"$");

	private static final Pattern ROUTE = Pattern.compile("^        - \\\"(GET|POST) ([^ ]+) → (task-\\d+\\.\\d+)\\\"$");

	private PlanGuardSupport() {
	}

	static Plan readPlan() throws IOException {
		List<String> lines = Files.readAllLines(TASKS);
		Map<String, MutableTask> tasks = new LinkedHashMap<>();
		List<RouteSpec> routes = new ArrayList<>();
		MutableTask current = null;
		boolean artifacts = false;
		for (String line : lines) {
			Matcher route = ROUTE.matcher(line);
			if (route.matches()) {
				routes.add(new RouteSpec(route.group(1), route.group(2), route.group(3)));
			}

			Matcher taskId = TASK_ID.matcher(line);
			if (taskId.matches()) {
				current = new MutableTask(taskId.group(1));
				tasks.put(current.id, current);
				artifacts = false;
				continue;
			}
			if (current == null) {
				continue;
			}
			if (line.equals("          status: COMPLETE")) {
				current.completeInPlan = true;
			}
			if (line.equals("          artifact:")) {
				artifacts = true;
				continue;
			}
			if (line.startsWith("          ") && !line.startsWith("            ") && !line.isBlank()) {
				artifacts = false;
			}
			if (artifacts) {
				Matcher item = QUOTED_ITEM.matcher(line);
				if (item.matches()) {
					current.artifacts.add(item.group(1));
				}
			}
			if (line.startsWith("          covers: {acs: [")) {
				int start = line.indexOf('[') + 1;
				int end = line.indexOf(']', start);
				if (end > start) {
					for (String ac : line.substring(start, end).split(",")) {
						if (!ac.isBlank()) {
							current.acs.add(ac.trim());
						}
					}
				}
			}
		}

		Set<String> completed = completedTasks(tasks);
		Map<String, TaskSpec> immutableTasks = new LinkedHashMap<>();
		for (MutableTask task : tasks.values()) {
			immutableTasks.put(task.id, new TaskSpec(task.id, List.copyOf(task.artifacts), Set.copyOf(task.acs),
					completed.contains(task.id)));
		}
		return new Plan(Map.copyOf(immutableTasks), List.copyOf(routes), Set.copyOf(completed));
	}

	private static Set<String> completedTasks(Map<String, MutableTask> tasks) throws IOException {
		Set<String> completed = new LinkedHashSet<>();
		for (MutableTask task : tasks.values()) {
			if (task.completeInPlan) {
				completed.add(task.id);
			}
		}
		Pattern completedLine = Pattern.compile("^- (task-\\d+\\.\\d+)$");
		for (String line : Files.readAllLines(STATUS)) {
			Matcher matcher = completedLine.matcher(line);
			if (matcher.matches()) {
				completed.add(matcher.group(1));
			}
		}
		return completed;
	}

	static String artifactPath(String declaration) {
		int qualifier = declaration.indexOf(" (");
		return qualifier < 0 ? declaration : declaration.substring(0, qualifier);
	}

	static String deletionTask(String declaration) {
		Matcher matcher = Pattern.compile("deleted by (task-\\d+\\.\\d+)").matcher(declaration);
		return matcher.find() ? matcher.group(1) : null;
	}

	static Set<String> citedAcceptanceCriteria(List<String> sourceLines) {
		Set<String> cited = new HashSet<>();
		Pattern token = Pattern.compile("AC-(\\d+)");
		Pattern methodToken = Pattern.compile("\\bvoid\\s+\\w*AC(\\d+)\\w*\\s*\\(");
		for (String line : sourceLines) {
			if (line.contains("DisplayName") || line.contains("@Tag")) {
				Matcher annotation = token.matcher(line);
				while (annotation.find()) {
					cited.add("AC-" + annotation.group(1));
				}
			}
			Matcher method = methodToken.matcher(line);
			while (method.find()) {
				cited.add("AC-" + method.group(1));
			}
		}
		return cited;
	}

	record Plan(Map<String, TaskSpec> tasks, List<RouteSpec> routes, Set<String> completed) {
	}

	record TaskSpec(String id, List<String> artifacts, Set<String> acs, boolean complete) {
	}

	record RouteSpec(String method, String path, String ownerTask) {
	}

	private static final class MutableTask {

		private final String id;

		private final List<String> artifacts = new ArrayList<>();

		private final Set<String> acs = new LinkedHashSet<>();

		private boolean completeInPlan;

		private MutableTask(String id) {
			this.id = id;
		}

	}

}
