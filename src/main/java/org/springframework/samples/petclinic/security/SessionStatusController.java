package org.springframework.samples.petclinic.security;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionStatusController {

	private static final long WARNING_THRESHOLD_SECONDS = 120L;

	@GetMapping({ "/auth/session-status", "/session/status" })
	public ResponseEntity<Map<String, Object>> getSessionStatus(HttpServletRequest request,
			Authentication authentication) {
		HttpSession session = request.getSession(false);
		if (session == null || authentication == null
				|| !(authentication.getPrincipal() instanceof PetClinicPrincipal principal)) {
			return ResponseEntity.ok(Map.of("authenticated", false, "remainingSeconds", 0L, "warningRequired", false,
					"passwordChangeRequired", false));
		}

		Long lastAccessTime = (Long) session.getAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME);
		long now = System.currentTimeMillis();
		long elapsedMs = lastAccessTime != null ? (now - lastAccessTime) : 0L;
		long remainingMs = Math.max(0L, InteractiveSessionFilter.SESSION_TIMEOUT_MS - elapsedMs);
		long remainingSeconds = remainingMs / 1000L;
		boolean warningRequired = remainingSeconds > 0 && remainingSeconds <= WARNING_THRESHOLD_SECONDS;

		return ResponseEntity.ok(Map.of("authenticated", true, "remainingSeconds", remainingSeconds, "warningRequired",
				warningRequired, "passwordChangeRequired", principal.isPasswordChangeRequired()));
	}

	@PostMapping({ "/auth/session-extend", "/session/extend" })
	public ResponseEntity<Map<String, String>> extendSession(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, System.currentTimeMillis());
			return ResponseEntity.ok(Map.of("status", "extended"));
		}
		return ResponseEntity.ok(Map.of("status", "no_session"));
	}

}
