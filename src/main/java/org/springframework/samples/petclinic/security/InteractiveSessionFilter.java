package org.springframework.samples.petclinic.security;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InteractiveSessionFilter extends OncePerRequestFilter {

	public static final String LAST_INTERACTIVE_ACCESS_TIME = "LAST_INTERACTIVE_ACCESS_TIME";

	public static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

	private static final Pattern NON_EXTENDING_POLLING_PATTERN = Pattern
		.compile("^/owner/requests/[^/]+/status(/.*)?$");

	private static final Pattern STATIC_RESOURCES_PATTERN = Pattern.compile("^/(resources|webjars|favicon\\.ico).*$");

	private final SessionVersionService sessionVersionService;

	public InteractiveSessionFilter(SessionVersionService sessionVersionService) {
		this.sessionVersionService = Objects.requireNonNull(sessionVersionService,
				"sessionVersionService must not be null");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String requestUri = request.getRequestURI();
		HttpSession session = request.getSession(false);

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		PetClinicPrincipal principal = null;
		if (authentication != null && authentication.getPrincipal() instanceof PetClinicPrincipal) {
			principal = (PetClinicPrincipal) authentication.getPrincipal();
		}

		if (session != null && principal != null) {
			// Check session version validity (e.g. invalidation on password reset)
			if (!this.sessionVersionService.isSessionVersionValid(principal.getId(), principal.getSessionVersion())) {
				session.invalidate();
				SecurityContextHolder.clearContext();
				response.sendRedirect(request.getContextPath() + "/auth/login?expired=true");
				return;
			}

			// Check interactive session timeout
			Long lastAccessTime = (Long) session.getAttribute(LAST_INTERACTIVE_ACCESS_TIME);
			long now = System.currentTimeMillis();

			if (lastAccessTime != null && (now - lastAccessTime) > SESSION_TIMEOUT_MS) {
				session.invalidate();
				SecurityContextHolder.clearContext();
				if (isStatusPolling(requestUri)) {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType("application/json");
					response.getWriter().write("{\"error\":\"session_expired\"}");
					return;
				}
				response.sendRedirect(request.getContextPath() + "/auth/login?expired=true");
				return;
			}

			// Update last interactive access time only for interactive (non-polling,
			// non-static) requests
			if (!isStatusPolling(requestUri) && !isStaticResource(requestUri) && !isSessionStatusEndpoint(requestUri)) {
				session.setAttribute(LAST_INTERACTIVE_ACCESS_TIME, now);
			}

			// Restrict temporary password accounts until password is changed
			if (principal.isPasswordChangeRequired()) {
				if (!isPermittedForPasswordChange(requestUri)) {
					response.sendRedirect(request.getContextPath() + "/auth/password-change");
					return;
				}
			}
		}
		else if (session != null && !isStaticResource(requestUri)) {
			// Unauthenticated session - record interactive access
			if (session.getAttribute(LAST_INTERACTIVE_ACCESS_TIME) == null) {
				session.setAttribute(LAST_INTERACTIVE_ACCESS_TIME, System.currentTimeMillis());
			}
		}

		filterChain.doFilter(request, response);
	}

	public static boolean isStatusPolling(String uri) {
		return uri != null && NON_EXTENDING_POLLING_PATTERN.matcher(uri).matches();
	}

	public static boolean isStaticResource(String uri) {
		return uri != null && STATIC_RESOURCES_PATTERN.matcher(uri).matches();
	}

	public static boolean isSessionStatusEndpoint(String uri) {
		return "/auth/session-status".equals(uri) || "/session/status".equals(uri);
	}

	private static boolean isPermittedForPasswordChange(String uri) {
		if (uri == null) {
			return false;
		}
		return uri.startsWith("/auth/password-change") || uri.startsWith("/account/password-change")
				|| uri.startsWith("/auth/logout") || uri.startsWith("/logout") || uri.startsWith("/auth/session")
				|| uri.startsWith("/session") || uri.startsWith("/resources") || uri.startsWith("/webjars")
				|| uri.startsWith("/error");
	}

}
