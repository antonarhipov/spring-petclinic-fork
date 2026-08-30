package org.springframework.samples.petclinic.support;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StaffHttpSupport {

	private static final Pattern CSRF = Pattern.compile(
			"name=[\"']_csrf[\"'][^>]*value=[\"']([^\"']+)[\"']|value=[\"']([^\"']+)[\"'][^>]*name=[\"']_csrf[\"']");

	private StaffHttpSupport() {
	}

	public static HttpClient loginAsAdmin(int port) throws Exception {
		return loginAsAdminWithCsrf(port).client();
	}

	public static StaffSession loginAsAdminWithCsrf(int port) throws Exception {
		HttpClient client = HttpClient.newBuilder()
			.cookieHandler(new CookieManager())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		HttpResponse<String> loginPage = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		String csrf = extractCsrf(loginPage.body());
		String body = "username=" + URLEncoder.encode("admin", StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode("admin123", StandardCharsets.UTF_8) + "&_csrf="
				+ URLEncoder.encode(csrf, StandardCharsets.UTF_8);
		client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login"))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(body))
			.build(), HttpResponse.BodyHandlers.ofString());
		HttpResponse<String> afterLogin = client.send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/login")).GET().build(),
				HttpResponse.BodyHandlers.ofString());
		return new StaffSession(client, extractCsrf(afterLogin.body()));
	}

	public static String extractCsrf(String html) {
		Matcher matcher = CSRF.matcher(html);
		if (!matcher.find()) {
			throw new IllegalStateException("CSRF token not found");
		}
		String token = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		if (token == null || token.isBlank()) {
			throw new IllegalStateException("CSRF token not found");
		}
		return token;
	}

	public record StaffSession(HttpClient client, String csrf) {
	}

}
