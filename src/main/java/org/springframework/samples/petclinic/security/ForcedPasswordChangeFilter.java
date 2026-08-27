package org.springframework.samples.petclinic.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ForcedPasswordChangeFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserPrincipal principal) {
			if (principal.isPasswordChangeRequired()) {
				String uri = request.getRequestURI();
				if (!isAllowedForPasswordChange(uri)) {
					response.sendRedirect(request.getContextPath() + "/change-password");
					return;
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean isAllowedForPasswordChange(String uri) {
		return uri.startsWith("/change-password") || uri.startsWith("/logout") || uri.startsWith("/resources/")
				|| uri.startsWith("/webjars/") || uri.startsWith("/error") || uri.startsWith("/login");
	}

}
