package org.springframework.samples.petclinic.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private final AccountService accountService;

	public CustomAuthenticationSuccessHandler(AccountService accountService) {
		this.accountService = accountService;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException, ServletException {

		accountService.recordSuccessfulLogin(authentication.getName());

		if (authentication.getPrincipal() instanceof UserPrincipal principal) {
			if (principal.isPasswordChangeRequired()) {
				getRedirectStrategy().sendRedirect(request, response, "/change-password");
				return;
			}
		}

		super.onAuthenticationSuccess(request, response, authentication);
	}

}
