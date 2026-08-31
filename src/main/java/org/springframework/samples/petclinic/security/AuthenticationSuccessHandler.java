package org.springframework.samples.petclinic.security;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.samples.petclinic.account.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		HttpSession session = request.getSession(true);
		session.setAttribute(InteractiveSessionFilter.LAST_INTERACTIVE_ACCESS_TIME, System.currentTimeMillis());

		if (authentication.getPrincipal() instanceof PetClinicPrincipal principal) {
			if (principal.isPasswordChangeRequired()) {
				getRedirectStrategy().sendRedirect(request, response, "/auth/password-change");
				return;
			}

			SavedRequest savedRequest = (SavedRequest) session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
			if (savedRequest != null) {
				String targetUrl = savedRequest.getRedirectUrl();
				if (principal.getRole() == Role.OWNER && targetUrl.contains("/owner/")) {
					super.onAuthenticationSuccess(request, response, authentication);
					return;
				}
				if (principal.getRole() == Role.STAFF && targetUrl.contains("/staff/")) {
					super.onAuthenticationSuccess(request, response, authentication);
					return;
				}
			}

			if (principal.getRole() == Role.OWNER) {
				getRedirectStrategy().sendRedirect(request, response, "/owner/dashboard");
				return;
			}
			else if (principal.getRole() == Role.STAFF) {
				getRedirectStrategy().sendRedirect(request, response, "/staff/calendar/week");
				return;
			}
		}

		getRedirectStrategy().sendRedirect(request, response, "/");
	}

}
