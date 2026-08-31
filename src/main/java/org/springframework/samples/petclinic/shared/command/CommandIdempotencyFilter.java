package org.springframework.samples.petclinic.shared.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Serializes authenticated browser commands by command ID and replays the canonical
 * response for a duplicate. The command transaction surrounds the controller's business
 * transaction so a completed record and its domain changes commit atomically.
 */
@Component
@ConditionalOnBean({ CommandService.class, BrowserCommandContext.class })
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class CommandIdempotencyFilter extends OncePerRequestFilter {

	private static final int MAX_REPLAY_BODY_LENGTH = 4000;

	private final CommandService commandService;

	private final BrowserCommandContext browserCommandContext;

	public CommandIdempotencyFilter(CommandService commandService, BrowserCommandContext browserCommandContext) {
		this.commandService = commandService;
		this.browserCommandContext = browserCommandContext;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !"POST".equalsIgnoreCase(request.getMethod()) || !isProtectedMutation(request.getRequestURI())
				|| request.getParameter("commandId") == null || isServiceManagedCommand(request.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		UUID commandId;
		try {
			commandId = UUID.fromString(request.getParameter("commandId"));
		}
		catch (IllegalArgumentException ex) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The command identifier is invalid.");
			return;
		}

		Long actorAccountId = this.browserCommandContext.currentActorAccountId();
		String target = request.getRequestURI();
		String requestHash = CommandService.computeRequestHash(canonicalRequest(request));
		ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);
		AtomicBoolean executed = new AtomicBoolean();

		try {
			CommandRecord command = this.commandService.executeOrReplay(commandId, actorAccountId, "HTTP_POST", target,
					requestHash,
					() -> execute(request, cachingResponse, filterChain, executed, actorAccountId, commandId));
			if (executed.get()) {
				cachingResponse.copyBodyToResponse();
			}
			else {
				replay(response, command);
			}
		}
		catch (CommandFilterException ex) {
			Throwable cause = ex.getCause();
			if (cause instanceof IOException ioException) {
				throw ioException;
			}
			if (cause instanceof ServletException servletException) {
				throw servletException;
			}
			throw ex;
		}
		catch (IllegalStateException ex) {
			if (!response.isCommitted()) {
				response.reset();
				response.sendError(HttpServletResponse.SC_CONFLICT,
						"This action was already submitted with different details.");
			}
		}
	}

	private CommandResult execute(HttpServletRequest request, ContentCachingResponseWrapper response,
			FilterChain filterChain, AtomicBoolean executed, Long actorAccountId, UUID commandId) {
		executed.set(true);
		try {
			filterChain.doFilter(request, response);
		}
		catch (IOException | ServletException ex) {
			throw new CommandFilterException(ex);
		}
		String body = replayableBody(response);
		CommandResult result = CommandResult.of(response.getStatus(), response.getContentType(), null,
				response.getHeader("Location"), body);
		String path = request.getRequestURI();
		this.browserCommandContext.recordStructuredEvent(actorAccountId, auditAction(path), auditTargetType(path),
				truncate(path, 100), response.getStatus() < 400 ? "SUCCESS" : "REJECTED", commandId,
				versionSnapshot(request), Map.of("status", response.getStatus(), "location",
						response.getHeader("Location") != null ? response.getHeader("Location") : path));
		return result;
	}

	private static void replay(HttpServletResponse response, CommandRecord command) throws IOException {
		response.setStatus(command.getResultStatus() != null ? command.getResultStatus() : HttpServletResponse.SC_OK);
		if (command.getResultLocation() != null) {
			response.setHeader("Location", command.getResultLocation());
		}
		if (command.getResultType() != null) {
			response.setContentType(command.getResultType());
		}
		if (command.getResultBody() != null) {
			response.getWriter().write(command.getResultBody());
		}
	}

	private static String replayableBody(ContentCachingResponseWrapper response) {
		byte[] bytes = response.getContentAsByteArray();
		if (bytes.length == 0) {
			return null;
		}
		String body = new String(bytes, response.getCharacterEncoding() != null
				? java.nio.charset.Charset.forName(response.getCharacterEncoding()) : StandardCharsets.UTF_8);
		return body.length() <= MAX_REPLAY_BODY_LENGTH ? body : null;
	}

	private static String canonicalRequest(HttpServletRequest request) {
		Map<String, String[]> parameters = new TreeMap<>(request.getParameterMap());
		parameters.remove("commandId");
		parameters.remove("_csrf");
		StringBuilder canonical = new StringBuilder(request.getRequestURI());
		parameters.forEach((name, values) -> {
			String[] canonicalValues = Arrays.copyOf(values, values.length);
			Arrays.sort(canonicalValues);
			canonical.append('|').append(name.length()).append(':').append(name);
			for (String value : canonicalValues) {
				canonical.append('=').append(value.length()).append(':').append(value);
			}
		});
		return canonical.toString();
	}

	private static Map<String, String> versionSnapshot(HttpServletRequest request) {
		Map<String, String> versions = new TreeMap<>();
		for (String field : new String[] { "expectedVersion", "workflowRevisionId", "queueVersion" }) {
			String value = request.getParameter(field);
			if (value != null) {
				versions.put(field, value);
			}
		}
		return versions;
	}

	private static String auditAction(String path) {
		return auditTargetType(path).toUpperCase() + "_COMMAND";
	}

	private static String auditTargetType(String path) {
		if (path.contains("/appointments")) {
			return "Appointment";
		}
		if (path.contains("/offers")) {
			return "Offer";
		}
		if (path.contains("/queue")) {
			return "QueueItem";
		}
		if (path.contains("/availability") || path.contains("clinic-policy")) {
			return "Availability";
		}
		if (path.contains("/requests")) {
			return "SchedulingRequest";
		}
		if (path.contains("password") || path.contains("/account/")) {
			return "Security";
		}
		return "OwnerAdministration";
	}

	private static String truncate(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private static boolean isProtectedMutation(String path) {
		return path.startsWith("/owner/") || path.startsWith("/staff/") || path.startsWith("/account/")
				|| path.startsWith("/auth/password-change");
	}

	private static boolean isServiceManagedCommand(String path) {
		return path.matches("/staff/owners/[^/]+/account/(provision|reset)");
	}

	private static final class CommandFilterException extends RuntimeException {

		private CommandFilterException(Exception cause) {
			super(cause);
		}

	}

}
