package org.springframework.samples.petclinic.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.samples.petclinic.PetClinicApplication;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Guards that shipped features are actually reachable by a person starting at the home
 * page. Controller-level MVC tests invoke handlers directly, so they pass even when no
 * page links to the handler and the feature is dead weight in the UI.
 */
// Pinned to the real application: this package also holds a nested @SpringBootApplication
// (CrashControllerIntegrationTests.TestConfiguration) that would otherwise win the
// configuration search and register only the two controllers in this package.
@SpringBootTest(classes = PetClinicApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NavigationReachabilityTests {

	private static final Pattern HREF = Pattern.compile("href=\"([^\"]*)\"");

	private static final Pattern TEMPLATE_DIR = Pattern.compile("src/main/resources/templates");

	/** Deliberately excluded: not GET navigation a crawler should follow. */
	private static final Set<String> NOT_NAVIGATION = Set.of("/oups", "/logout");

	private static final List<String> STATIC_PREFIXES = List.of("/resources", "/webjars", "/actuator", "/css",
			"/images");

	private static final int MAX_PAGES = 200;

	@Autowired
	MockMvc mockMvc;

	@Test
	@WithMockUser(username = "admin", roles = "STAFF")
	void staffReachEveryWorkspaceFromTheHomePage() throws Exception {
		Crawl crawl = crawlFrom("/");
		assertThat(crawl.failures()).as("pages linked from staff navigation must not error").isEmpty();
		assertThat(crawl.visited()).as("staff workspaces reachable by clicking from /")
			.contains("/staff/queue", "/staff/calendar", "/staff/availability", "/staff/settings");
	}

	@Test
	@WithMockUser(username = "george", roles = "OWNER")
	void ownersReachTheBookingFormFromTheHomePage() throws Exception {
		Crawl crawl = crawlFrom("/");
		assertThat(crawl.failures()).as("pages linked from owner navigation must not error").isEmpty();
		assertThat(crawl.visited()).as("owner scheduling pages reachable by clicking from /")
			.contains("/owner/dashboard", "/owner/appointments", "/owner/history", "/owner/profile");
		assertThat(crawl.visited()).as("a pet's booking form must be reachable by clicking from /")
			.anyMatch(path -> path.matches("/owner/pets/\\d+/scheduling-requests/new"));
	}

	@Test
	void everyFragmentTemplateIsInsertedBySomeTemplate() throws IOException {
		Path templates = Path.of("src/main/resources/templates");
		Map<Path, String> corpus = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(templates)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
				corpus.put(file, Files.readString(file));
			}
		}
		Map<String, Path> orphans = new LinkedHashMap<>();
		try (Stream<Path> fragments = Files.list(templates.resolve("fragments"))) {
			for (Path fragment : fragments.filter(path -> path.toString().endsWith(".html")).toList()) {
				String name = fragment.getFileName().toString().replace(".html", "");
				boolean inserted = corpus.entrySet()
					.stream()
					.anyMatch(entry -> !entry.getKey().equals(fragment)
							&& entry.getValue().contains("fragments/" + name));
				if (!inserted) {
					orphans.put(name, fragment);
				}
			}
		}
		assertThat(orphans).as("fragment templates that no page inserts are invisible to users").isEmpty();
	}

	private Crawl crawlFrom(String start) throws Exception {
		Set<String> visited = new LinkedHashSet<>();
		Map<String, Integer> failures = new LinkedHashMap<>();
		Deque<String> pending = new ArrayDeque<>();
		pending.add(start);
		while (!pending.isEmpty() && visited.size() < MAX_PAGES) {
			String path = pending.poll();
			if (!visited.add(path)) {
				continue;
			}
			MvcResult result = this.mockMvc.perform(get(path).accept(MediaType.TEXT_HTML)).andReturn();
			int status = result.getResponse().getStatus();
			if (status >= 400) {
				failures.put(path, status);
				continue;
			}
			if (status >= 300) {
				continue;
			}
			for (String link : linksIn(result.getResponse().getContentAsString(), path)) {
				if (!visited.contains(link)) {
					pending.add(link);
				}
			}
		}
		return new Crawl(visited, failures);
	}

	private static Set<String> linksIn(String html, String currentPath) {
		Set<String> links = new LinkedHashSet<>();
		Matcher matcher = HREF.matcher(html);
		while (matcher.find()) {
			String href = matcher.group(1).trim();
			int fragment = href.indexOf('#');
			if (fragment >= 0) {
				href = href.substring(0, fragment);
			}
			if (href.isEmpty() || href.startsWith("http") || href.startsWith("//") || href.startsWith("mailto:")
					|| href.startsWith("javascript:")) {
				continue;
			}
			String path = href.startsWith("/") ? href : resolveRelative(currentPath, href);
			if (NOT_NAVIGATION.contains(path) || STATIC_PREFIXES.stream().anyMatch(path::startsWith)) {
				continue;
			}
			links.add(path);
		}
		return links;
	}

	private static String resolveRelative(String currentPath, String href) {
		int lastSlash = currentPath.lastIndexOf('/');
		String base = lastSlash <= 0 ? "/" : currentPath.substring(0, lastSlash + 1);
		return base.endsWith("/") ? base + href : base + "/" + href;
	}

	private record Crawl(Set<String> visited, Map<String, Integer> failures) {
	}

}
