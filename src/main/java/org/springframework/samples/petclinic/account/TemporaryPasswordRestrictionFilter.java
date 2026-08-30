package org.springframework.samples.petclinic.account;

import java.io.IOException;
import java.util.Locale;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class TemporaryPasswordRestrictionFilter extends OncePerRequestFilter {

	private final AccountRepository accounts;

	public TemporaryPasswordRestrictionFilter(AccountRepository accounts) {
		this.accounts = accounts;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& !(authentication instanceof AnonymousAuthenticationToken) && mustChangePassword(authentication)
				&& !allowed(request)) {
			response.sendRedirect(request.getContextPath() + "/account/password/change");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private boolean mustChangePassword(Authentication authentication) {
		if (authentication.getPrincipal() instanceof PetClinicUserDetails details) {
			return details.isMustChangePassword();
		}
		return this.accounts.findByUsername(authentication.getName().toLowerCase(Locale.ROOT))
			.map(Account::isMustChangePassword)
			.orElse(false);
	}

	private static boolean allowed(HttpServletRequest request) {
		String path = request.getServletPath();
		if (path == null || path.isEmpty()) {
			path = request.getRequestURI();
		}
		return "/account/password/change".equals(path) || "/logout".equals(path) || "/login".equals(path)
				|| "/error".equals(path) || path.startsWith("/resources/") || path.startsWith("/webjars/")
				|| path.startsWith("/css/") || path.startsWith("/images/");
	}

}
