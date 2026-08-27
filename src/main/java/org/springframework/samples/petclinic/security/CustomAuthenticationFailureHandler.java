package org.springframework.samples.petclinic.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

	private final AccountService accountService;

	public CustomAuthenticationFailureHandler(AccountService accountService) {
		super("/login?error=true");
		this.accountService = accountService;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException, ServletException {

		String username = request.getParameter("username");
		if (username != null && !username.trim().isEmpty()) {
			accountService.recordFailedLogin(username);
		}

		super.onAuthenticationFailure(request, response, exception);
	}

}
