package org.springframework.samples.petclinic.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class TemporaryPasswordFilter extends OncePerRequestFilter {

	private final ObjectProvider<AccountRepository> accounts;

	TemporaryPasswordFilter(ObjectProvider<AccountRepository> accounts) {
		this.accounts = accounts;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String path = request.getRequestURI();
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken) && !path.equals("/password/change")
				&& !path.equals("/logout") && this.accounts.getIfAvailable() != null
				&& this.accounts.getIfAvailable()
					.findByUsername(authentication.getName())
					.map(Account::isMustChangePassword)
					.orElse(false)) {
			response.sendRedirect("/password/change");
			return;
		}
		filterChain.doFilter(request, response);
	}

}
